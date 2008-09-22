/*
 * Copyright 2007-2008 Sun Microsystems, Inc.
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

package com.sun.sgs.service;


/**
 * This is the base interface used for all services. Services support
 * specific funcationality and work in a transactional context. 
 * See {@code TransactionParticipant} for details on when interaction
 * between {@code Service}s is allowed.
 * <p>
 * On startup of an application, services are constructed (see details
 * below). This provides access to the non-transactional core components
 * of the system as well as the other {@code Service}s that have already
 * been created. {@code Service}s are created in a known order based
 * on dependencies: {@code DataService}, {@code WatchdogService},
 * {@code NodeMappingService}, {@code TaskService},
 * {@code ClientSessionService}, and the {@code ChannelManager},
 * finishing with any custom {@code Service}s ordered based on the
 * application's configuration.
 * <p>
 * All implementations of {@code Service} must have a constructor with
 * parameters of types {@code Properties}, {@code ComponentRegistry}, and
 * {@code TransactionProxy}. This is how the {@code Service} is created
 * on startup. The {@code Properties} parameter provides application and
 * service-specific properties. The {@code ComponentRegistry} provides
 * access to non-transactional kernel and system components like the
 * {@code TransactionScheduler}. The {@code TransactionProxy} provides
 * access to transactional state (when active) and the other available
 * {@code Service}s. If any error occurs in creating a {@code Service},
 * the constructor may throw any {@code Exception}, causing the application
 * to shutdown.
 * <p>
 * Note that {@code Service}s are not created in the context of a
 * transaction. If a given constructor needs to do any work transactionally,
 * it may do so by calling {@code TransactionScheduler.runTask}.
 */
public interface Service {

    /**
     * Returns the name used to identify this service.
     *
     * @return the service's name
     */
    String getName();

    /**
     * Notifies this {@code Service} that the application is fully
     * configured and ready to start running. This means that all other {@code
     * Service}s associated with this application have been successfully
     * created. If the method throws an exception, then the application will be
     * shutdown.
     *
     * @throws Exception if an error occurs
     */
    void ready() throws Exception;

    /** 
     * Attempts to shut down this service, returning a value indicating whether
     * the attempt was successful.  The call will throw {@link
     * IllegalStateException} if a call to this method has already completed
     * with a return value of {@code true}. <p>
     *
     * This method does not require a transaction, and should not be called
     * from one because this method will typically not succeed if there are
     * outstanding transactions. <p>
     *
     * Typical implementations will refuse to accept calls associated with
     * transactions that were not joined prior to the {@code shutdown}
     * call by throwing an {@code IllegalStateException}, and will wait
     * for already joined transactions to commit or abort before returning,
     * although the precise behavior is implementation specific.
     * Implementations are also permitted, but not required, to return
     * {@code false} if {@link Thread#interrupt Thread.interrupt} is
     * called on a thread that is currently blocked within a call to this
     * method. <p>
     *
     * Callers should assume that, in a worst case, this method may block
     * indefinitely, and so should arrange to take other action (for example,
     * calling {@link System#exit System.exit}) if the call fails to complete
     * successfully in a certain amount of time.
     *
     * @return	{@code true} if the shut down was successful, else
     *		{@code false}
     * @throws	IllegalStateException if the {@code shutdown} method has
     *		already been called and returned {@code true}
     */
    boolean shutdown();

}
