/*
 * Copyright 2007 Sun Microsystems, Inc.
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

package com.sun.sgs.impl.profile.listener;

import com.sun.sgs.impl.sharedutil.PropertiesWrapper;

import com.sun.sgs.impl.profile.util.NetworkReporter;

import com.sun.sgs.kernel.KernelRunnable;
import com.sun.sgs.kernel.RecurringTaskHandle;
import com.sun.sgs.kernel.ResourceCoordinator;
import com.sun.sgs.kernel.TaskOwner;
import com.sun.sgs.kernel.TaskScheduler;

import com.sun.sgs.profile.ProfileOperation;
import com.sun.sgs.profile.ProfileListener;
import com.sun.sgs.profile.ProfileReport;

import java.beans.PropertyChangeEvent;

import java.io.IOException;

import java.util.Formatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;


/**
 * This implementation of <code>ProfileListener</code> takes
 * snapshots at fixed intervals. It provides a very simple view of what
 * tasks have run over the last interval. By default the time
 * interval is 5 seconds.
 * <p>
 * This listener reports its findings on a server socket. Any number of
 * users may connect to that socket to watch the reports. The default
 * port used is 43010.
 * <p>
 * The <code>com.sun.sgs.impl.profile.listener.SnapshotTaskListener.</code>
 * root is used for all properties in this class. The <code>report.port</code>
 * key is used to specify an alternate port on which to report profiling
 * data. The <code>report.period</code> key is used to specify the length of
 * time, in milliseconds, between reports.
 */
public class SnapshotTaskListener implements ProfileListener {

    // the reporter used to publish data
    private NetworkReporter networkReporter;

    // the handle for the recurring reporting task
    private RecurringTaskHandle handle;

    // the base name for properties
    private static final String PROP_BASE =
        SnapshotTaskListener.class.getName();

    // the supported properties and their default values
    private static final String PORT_PROPERTY = PROP_BASE + ".report.port";
    private static final int DEFAULT_PORT = 43010;
    private static final String PERIOD_PROPERTY = PROP_BASE + ".report.period";
    private static final long DEFAULT_PERIOD = 5000;

    private HashMap<String,TaskDetail> map;

    /**
     * Creates an instance of {@code RuntimeHistogramListener}.
     *
     * @param properties the {@code Properties} for this listener
     * @param owner the {@code TaskOwner} to use for all tasks run by
     *        this listener
     * @param taskScheduler the {@code TaskScheduler} to use for
     *        running short-lived or recurring tasks
     * @param resourceCoord the {@code ResourceCoordinator} used to
     *        run any long-lived tasks
     * @throws IOException if the server socket cannot be created
     */
    public SnapshotTaskListener(Properties properties, TaskOwner owner,
                                TaskScheduler taskScheduler,
                                ResourceCoordinator resourceCoord)
        throws IOException
    {
        PropertiesWrapper wrappedProps = new PropertiesWrapper(properties);

        map = new HashMap<String,TaskDetail>();

        int port = wrappedProps.getIntProperty(PORT_PROPERTY, DEFAULT_PORT);
        networkReporter = new NetworkReporter(port, resourceCoord);

        long reportPeriod =
            wrappedProps.getLongProperty(PERIOD_PROPERTY, DEFAULT_PERIOD);
        handle = taskScheduler.
            scheduleRecurringTask(new TaskRunnable(), owner, 
                                  System.currentTimeMillis() + reportPeriod,
                                  reportPeriod);
        handle.start();
    }

    /**
     * {@inheritDoc}
     */
    public void propertyChange(PropertyChangeEvent event) {
	// unused
    }

    /**
     * Records details about successful tasks.
     *
     * @param profileReport the summary for the finished {@code Task}
     */
    public void report(ProfileReport profileReport) {
        if (profileReport.wasTaskSuccessful()) {
            String name = profileReport.getTask().getBaseTaskType();
            if (! name.startsWith("com.sun.sgs.impl.kernel")) {
                synchronized (map) {
                    TaskDetail detail = map.get(name);
                    if (detail == null) {
                        detail = new TaskDetail();
                        map.put(name, detail);
                    }
                    detail.count++;
                    detail.time += profileReport.getRunningTime();
                    detail.opCount +=
                        profileReport.getReportedOperations().size();
                    detail.retries += profileReport.getRetryCount();
		    for (ProfileOperation op :
                              profileReport.getReportedOperations()) {
			 Long l = detail.ops.get(op);
			 detail.ops.put(
			     op, Long.valueOf(l == null ? 1 : l + 1));
		    }		    
                }
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    public void shutdown() {
	// unused
    }

    private class TaskDetail {
        long count = 0;
        long time = 0;
        long opCount = 0;
        long retries = 0;
	Map<ProfileOperation,Long> ops = new HashMap<ProfileOperation,Long>();

        public String toString() {
            double avgTime = (double)time / (double)count;
            double avgOps = (double)opCount / (double)count;
	    Formatter formatter = new Formatter();
	    formatter.format(" avgTime=%2.2fms", avgTime);
	    formatter.format(" avgOps=%2.2f", avgOps);
	    formatter.format(" [%d/%d]", count, retries);
            if (opCount > 0)
		formatter.format("%n  ");
	    for (ProfileOperation op : ops.keySet()) {
		formatter.format(
		    "%s=%2.2f%% ",
		    op,
		    100.0 * (double)(ops.get(op).longValue()) / 
		    (double)opCount);
	    }		 
            return formatter.toString();
        }
    }

    /**
     *
     */
    private class TaskRunnable implements KernelRunnable {
        public String getBaseTaskType() {
            return TaskRunnable.class.getName();
        }
        public void run() throws Exception {
            Formatter reportStr = new Formatter();
            synchronized (map) {
                for (Entry<String,TaskDetail> entry : map.entrySet())
		    reportStr.format(
			"%s%s%n", entry.getKey(), entry.getValue());
                map.clear();
            }
            reportStr.format("%n");
            networkReporter.report(reportStr.toString());
        }
    }

}
