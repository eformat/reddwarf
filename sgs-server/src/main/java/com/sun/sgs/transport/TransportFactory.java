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

package com.sun.sgs.transport;

import java.lang.reflect.Constructor;
import java.util.Properties;

/**
 * A factory for creating {@link Transport} instances.
 */
public class TransportFactory {
    
    private TransportFactory() {}
    
    /**
     * Create a new transport.
     * The transport name must resolve to a class that implements
     * {@link Transport}. The class should be public, not abstract, and should
     * provide a public constructor with a {@link Properties} parameter.
     * The newly created transport object
     * is returned and each call will return a new instance.
     * 
     * @param transportClassName name of the class that implements
     * {@link Transport}
     * @param properties properties passed to the transport's constructor
     * @return the transport object
     * @throws NullPointerException if any argument is {@code null}
     * @throws ClassNotFoundException if the class specified by
     * {@code transportClassName} can not be found
     * @throws IllegalArgumentException if the class specified by
     * {@code transportClassName} does not implement {@link Transport}
     * @throws NoSuchMethodException is the class specified by
     * {@code transportClassName} does not have the required constructor
     * @throws InvocationTargetException if an exception is thrown from the
     * transport's constructor
     */
    static public Transport newTransport(String transportClassName,
                                    Properties properties)
        throws Exception
    {
        if (properties == null) {
            throw new NullPointerException("properties is null");
        } else if (transportClassName == null) {
            throw new NullPointerException("transportClassName is null");
        }        
        Class<?> transportClass = Class.forName(transportClassName);
    
        if (!Transport.class.isAssignableFrom(transportClass))
            throw new IllegalArgumentException(transportClassName +
                               " class does not implement Transport interface");
        
        Constructor<?> [] constructors = transportClass.getConstructors();
        Constructor<?> transportConstructor = null;
        for (int i = 0; i < constructors.length; i++) {
            Class<?> [] types = constructors[i].getParameterTypes();
            if (types.length == 1) {
                if (types[0].isAssignableFrom(Properties.class)) {
                    transportConstructor = constructors[i];
                    break;
                }
            }
        }

        if (transportConstructor == null)
            throw new NoSuchMethodException("Could not find a constructor for "
                                            + transportClass);
        
        return (Transport)transportConstructor.newInstance(properties);
    }
}
