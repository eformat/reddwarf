/*
 * Copyright 2007-2009 Sun Microsystems, Inc.
 *
 * This file is part of Project Darkstar Server.
 *
 * Project Darkstar Server is free software: you can redistribute it
 * and/or modify it under the terms of the GNU General Public License
 * version 2 as published by the Free Software Foundation and
 * distributed hereunder to you.
 *
 * Project Darkstar Server is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.sun.sgs.impl.service.nodemap.affinity;

import com.sun.sgs.auth.Identity;
import com.sun.sgs.impl.util.Exporter;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * The server portion of the distributed label propagation algorithm.
 * <p>
 * The server is known to each node participating in the algorithm.  It is
 * responsible for preparing the nodes for a run of the algorithm, coordinating
 * the iterations of the algorithm, and collecting and merging results from
 * each node when finished.
 */
public class LabelPropagationServer implements AffinityGroupFinder, LPAServer {
    /** The default value of the server port. */
    static final int DEFAULT_SERVER_PORT = 44537;

    /** The name we export ourselves under. */
    static final String SERVER_EXPORT_NAME = "LabelPropagationServer";

    // The exporter for this server
    private final Exporter<LPAServer> exporter;
    
    // A map from node id to client proxy objects.
    private final Map<Long, LPAClient> clientProxyMap =
            new ConcurrentHashMap<Long, LPAClient>();
    // A map from node id to proxy objects used by other nodes.
    private final Map<Long, LPAProxy> lpaProxyMap =
            new ConcurrentHashMap<Long, LPAProxy>();

    // A barrier that consists of the set of nodes we expect to hear back
    // from.  Once this set is empty, we can move on to the next step of
    // the algorithm.  This is required, rather than a simple Barrier, because
    // our calls must be idempotent.
    // This is replaced at each iteration.
    private Set<Long> nodeBarrier;

    // A latch to ensure our main thread waits for all nodes to complete
    // each step of the algorithm before proceeding.
    // This is replaced on each iteration.
    private CountDownLatch latch;

    // Algorithm iteration information
    private int currentIteration;
    private boolean nodesConverged;

    // Set to true if something has gone wrong and the results from
    // this algorithm run should be ignored
    private boolean failed;
    
    // A thread pool.  Will create as many threads as needed (I was having
    // starvation problems using a fixed thread pool - JANE?), with a timeout
    // of 60 sec before unused threads are reaped.
    private final ExecutorService executor = Executors.newCachedThreadPool();

    public LabelPropagationServer() throws IOException {
        // Export ourself.
        exporter = new Exporter<LPAServer>(LPAServer.class);
        exporter.export(this, SERVER_EXPORT_NAME, DEFAULT_SERVER_PORT);
    }

    // ---- Implement AffinityGroupFinder --- //
    /** {@inheritDoc} */
    // JANE does this need to be synchronized on something?
    public Collection<AffinityGroup> findAffinityGroups() {
        // Don't pay any attention to changes while we're running, at least
        // to start with.  If a node fails, we'll stop and return no information
        // for now.  JANE does that make sense?  If a node fails, a lot of
        // churn will be created.
        final Set<Long> clientIdSet;
        final Collection<LPAClient> clientProxySet;
        synchronized (clientProxyMap) {
            clientIdSet = clientProxyMap.keySet();
            clientProxySet = clientProxyMap.values();
        }
        final int clientSize = clientIdSet.size();

        Set<Long> clean = new HashSet<Long>(clientSize);
        for (Long id : clientIdSet) {
            clean.add(id);
        }

        // This server controls the running of the distributed label
        // propagation algorithm, using the LPAServer and LPAClient
        // interfaces.  The protocol is:
        // Server calls each LPAClient.exchangeCrossNodeInfo().
        //     Nodes contact other nodes which their graphs might be
        //     connected to, using LPAProxy.crossNodeEdges.
        //     Nodes can find the appropriate LPAProxy by calling
        //     LPAServer.getLPAProxy.
        //     When finished exchanging information, each node calls
        //     LPAServer.readyToBegin().
        // Server begins iterations of the algorithm.  For each iteration,
        // it calls LPAClient.startIteration().
        //     Nodes compute one iteration of the label propagation algorithm.
        //     Remote information (cross node edges discovered above) can
        //     be found by calling LPAProxy.getRemoteLabels on other nodes.
        //     When finished, each node calls LPAServer.finishedIteration,
        //     noting whether it believes the algorithm has converged.
        // When all nodes agree that the algorithm has converged, or many
        // iterations have been run, the server gathers all group information
        // from each node by calling LPAClient.affinityGroups().  The server
        // combines groups that might cross nodes, and creates new, final
        // affinity group information.
        
        failed = false;
        nodesConverged = false;
        // Tell each node to exchange their cross node information.  This
        // allows each node to have symmetric information:  e.g. if node 1
        // has a data conflict on obj1 with node 2, it lets node 2 know it
        // has a similar confict with node 1.
        nodeBarrier = Collections.synchronizedSet(new HashSet<Long>(clean));
        latch = new CountDownLatch(clientSize);
        for (final LPAClient proxy : clientProxySet) {
            executor.execute(new Runnable() {
                public void run() {
                    try {
                        proxy.exchangeCrossNodeInfo();
                    } catch (IOException ioe) {
                        failed = true;
                        // JANE should retry a few times, then
                        // assume the node has failed.
                        // Will want a way to contact the watchdog to
                        // let it know.
                    }
                }
            });
        }

        // Wait for the initialization to complete on all nodes
        try {
            // Completely arbitrary timeout!
            latch.await(10, TimeUnit.HOURS);
        } catch (InterruptedException ex) {
            failed = true;
        }

        // Start our algorithm iterations
        currentIteration = 1;
        while (!failed && !nodesConverged) {
            // Assume we'll converge unless told otherwise
            nodesConverged = true;
            nodeBarrier = Collections.synchronizedSet(new HashSet<Long>(clean));
            latch = new CountDownLatch(clientSize);
            for (final LPAClient proxy : clientProxySet) {
                executor.execute(new Runnable() {
                    public void run() {
                        try {
                            proxy.startIteration(currentIteration);
                        } catch (IOException ioe) {
                            // JANE should retry a few times, then
                            // assume the node has failed.
                            // Will want a way to contact the watchdog to
                            // let it know.
                            failed = true;
                        }
                    }
                });
            }
            // Wait for all nodes to complete this iteration
            try {
                // Completely arbitrary timeout!
                latch.await(10, TimeUnit.HOURS);
            } catch (InterruptedException ex) {
                failed = true;
            }
            // Completely arbitrary number to ensure we actually converge
            // This can probably be much lower.
            if (++currentIteration > 100) {
                break;
            }
        }

        // Now, gather up our results
        if (failed) {
            return new HashSet<AffinityGroup>();
        }

        // If, after this point, we cannot contact a node, simply
        // return the information that we have.   JANE is that safe?
        // Assuming a node has failed, we won't report the identities
        // on the failed node as being part of any group.  On the other
        // hand, we'll have lost all information about those identities
        // and will have to rebuild their object connections.

        final Set<AffinityGroup> returnedGroups =
                Collections.synchronizedSet(new HashSet<AffinityGroup>());
        latch = new CountDownLatch(clientSize);
        for (final LPAClient proxy : clientProxySet) {
            executor.execute(new Runnable() {
                public void run() {
                    try {
                        returnedGroups.addAll(proxy.affinityGroups());
                    } catch (IOException ioe) {
                        failed = true;
                        // JANE should retry a few times, then
                        // assume the node has failed.
                        // Will want a way to contact the watchdog to
                        // let it know.
                    } finally {
                        // this will need to change when we have retries
                        latch.countDown();
                    }
                }
            });
        }

        // Wait for the calls to complete on all nodes
        try {
            // Completely arbitrary timeout!
            latch.await(10, TimeUnit.HOURS);
        } catch (InterruptedException ex) {
            failed = true;
        }

        Map<Long, AffinityGroupImpl> groupMap =
                new HashMap<Long, AffinityGroupImpl>();
        for (AffinityGroup ag : returnedGroups) {
            long id = ag.getId();
            AffinityGroupImpl group = groupMap.get(id);
            if (group == null) {
                group = new AffinityGroupImpl(id);
                groupMap.put(id, group);
            }
            for (Identity gid : ag.getIdentities()) {
                group.addIdentity(gid);
            }
        }

        // convert our types
        Collection<AffinityGroup> retVal = new HashSet<AffinityGroup>();
        for (AffinityGroupImpl agi : groupMap.values()) {
            retVal.add(agi);
        }
        return retVal;
    }

    /** {@inheritDoc} */
    public void removeNode(long nodeId) {
        synchronized (clientProxyMap) {
            clientProxyMap.remove(nodeId);
            lpaProxyMap.remove(nodeId);
        }
    }

    /** {@inheritDoc} */
    public void shutdown() {
        executor.shutdownNow();
        exporter.unexport();
    }

    // --- Implement LPAServer --- //

    /** {@inheritDoc} */
    public void readyToBegin(long nodeId) throws IOException {
        if (nodeBarrier.remove(nodeId)) {
            latch.countDown();
        }
    }

    /** {@inheritDoc} */
    public void finishedIteration(long nodeId, boolean converged, int iteration)
            throws IOException
    {
        if (iteration != currentIteration) {
            // THINGS ARE VERY CONFUSED - need to log this
            failed = true;
        }
        nodesConverged = converged && nodesConverged;

        if (nodeBarrier.remove(nodeId)) {
            latch.countDown();
        }
    }

    /** {@inheritDoc} */
    public LPAProxy getLPAProxy(long nodeId) throws IOException {
        return lpaProxyMap.get(nodeId);
    }


    /** {@inheritDoc} */
    public void register(long nodeId, LPAClient client, LPAProxy proxy)
            throws IOException
    {
        synchronized (clientProxyMap) {
            clientProxyMap.put(nodeId, client);
            lpaProxyMap.put(nodeId, proxy);
        }
    }
}
