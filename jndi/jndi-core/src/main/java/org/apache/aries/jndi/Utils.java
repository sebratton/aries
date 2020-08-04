/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.aries.jndi;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;
import org.osgi.framework.BundleReference;
import org.osgi.service.jndi.JNDIConstants;

import javax.naming.NamingException;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 */
public final class Utils {

    /**
     * Ensure no one constructs us
     */
    private Utils() {
        throw new RuntimeException();
    }

    /**
     * @param env
     * @return the bundle context for the caller.
     * @throws NamingException
     */
    public static BundleContext getBundleContext(final Map<?, ?> env, final Class<?> namingClass) {
        return doPrivileged( new Gen<BundleContext>() {
        	public BundleContext f() {
        		return doGetBundleContext(env, namingClass);
        	}
        }
        );
    }

    private static BundleContext doGetBundleContext(Map<?, ?> env, Class<?> namingClass) {
        BundleContext result;

        Object bc = (env == null) ? null : env.get(JNDIConstants.BUNDLE_CONTEXT);

        if (bc != null && bc instanceof BundleContext) {
            result = (BundleContext) bc;
        } else {
            ClassLoader cl = Thread.currentThread().getContextClassLoader();
            result = getBundleContext(cl);
        }

        if (result == null) {
            StackFinder finder = new StackFinder();
            Class<?>[] classStack = finder.getClassContext();

            // working from the root of the stack look for the first instance in the stack of this class
            int i = classStack.length - 1;
            for (; i >= 0; i--) {
                if (namingClass.isAssignableFrom(classStack[i])) {
                    break;
                }
            }

            // then go to the parent of the namingClass down the stack until we find a BundleContext
            for (i++; i < classStack.length && result == null; i++) {
                result = getBundleContext(classStack[i].getClassLoader());
            }
        }

        return result;
    }

    private static BundleContext getBundleContext(ClassLoader cl2) {
        ClassLoader cl = cl2;
        BundleContext result = null;
        while (result == null && cl != null) {
            if (cl instanceof BundleReference) {
                Bundle b = ((BundleReference) cl).getBundle();
                result = b.getBundleContext();
                if (result == null) {
                    try {
                        b.start();
                        result = b.getBundleContext();
                    } catch (BundleException e) {
                    }
                    break;
                }
            } else if (cl != null) {
                cl = cl.getParent();
            }
        }

        return result;
    }

    public static String getSystemProperty(final String key, final String defaultValue) {
        return doPrivileged( new Gen<String> () {
        	public String f() {
        		return System.getProperty(key, defaultValue); 
        	}
        });
    }

    public static Hashtable<?, ?> toHashtable(Map<?, ?> map) {
        Hashtable<?, ?> env;
        if (map instanceof Hashtable<?, ?>) {
            env = (Hashtable<?, ?>) map;
        } else if (map == null) {
            env = new Hashtable<>();
        } else {
            env = new Hashtable<Object, Object>(map);
        }
        return env;
    }

    public static <T> T doPrivileged(final Gen<T> action) {
    	if (System.getSecurityManager() != null) {
    		return AccessController.doPrivileged(new PrivilegedAction<T>() 
    		{
    			public T run() {
    				return action.f();
    			}
    		});
    	} else {
    		return action.f();
    	}
    }

    public interface Callable<V, E extends Exception> {
        /**
         * Computes a result, or throws an exception if unable to do so.
         *
         * @return computed result
         * @throws E if unable to compute a result
         */
        V call() throws E;
    }

    @SuppressWarnings("unchecked")
    public static <T, E extends Exception> T doPrivilegedE(final Callable<T, E> action) throws E {
        if (System.getSecurityManager() != null) {
            try {
                return AccessController.doPrivileged( new PrivilegedExceptionAction<T>() {
					@Override
					public T run() throws Exception {
						return action.call();
					}
                });            
            } catch (PrivilegedActionException e) {
                throw (E) e.getException();
            }
        } else {
            return action.call();
        }
    }

    private static class StackFinder extends SecurityManager {
        public Class<?>[] getClassContext() {
            return super.getClassContext();
        }
    }

    public static <U, V> Iterator<V> map(Iterator<U> iterator, Fn<U, V> mapper) {
        return new MappedIterator<>(iterator, mapper);
    }

    private static class MappedIterator<U, V> implements Iterator<V> {

        private final Iterator<U> iterator;
        private final Fn<U, V> mapper;
        private V nextElement;
        private boolean hasNext;

        public MappedIterator(Iterator<U> iterator, Fn<U, V> mapper) {
            this.iterator = iterator;
            this.mapper = mapper;
            nextMatch();
        }

        @Override
        public boolean hasNext() {
            return hasNext;
        }

        @Override
        public V next() {
            if (!hasNext) {
                throw new NoSuchElementException();
            }
            return nextMatch();
        }

        private V nextMatch() {
            V oldMatch = nextElement;
            while (iterator.hasNext()) {
                V o = mapper.f(iterator.next());
                if (o != null) {
                    hasNext = true;
                    nextElement = o;
                    return oldMatch;
                }
            }
            hasNext = false;
            return oldMatch;
        }
    }
}
