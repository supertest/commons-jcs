package org.apache.jcs.engine;

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

import java.io.IOException;
import java.io.Serializable;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.jcs.engine.behavior.ICacheElement;
import org.apache.jcs.engine.behavior.ICacheEventQueue;
import org.apache.jcs.engine.behavior.ICacheListener;

/**
 * An abstract base class to the different implementations
 */
public abstract class AbstractCacheEventQueue
    implements ICacheEventQueue
{
    /** The logger. */
    protected static final Log log = LogFactory.getLog( AbstractCacheEventQueue.class );

    /** default */
    protected static final int DEFAULT_WAIT_TO_DIE_MILLIS = 10000;

    /**
     * time to wait for an event before snuffing the background thread if the queue is empty. make
     * configurable later
     */
    protected int waitToDieMillis = DEFAULT_WAIT_TO_DIE_MILLIS;

    /**
     * When the events are pulled off the queue, the tell the listener to handle the specific event
     * type. The work is done by the listener.
     */
    protected ICacheListener listener;

    /** Id of the listener registered with this queue */
    protected long listenerId;

    /** The cache region name, if applicable. */
    protected String cacheName;

    /** Maximum number of failures before we buy the farm. */
    protected int maxFailure;

    /** in milliseconds */
    protected int waitBeforeRetry;

    /** this is true if there is no worker thread. */
    protected boolean destroyed = true;

    /**
     * This means that the queue is functional. If we reached the max number of failures, the queue
     * is marked as non functional and will never work again.
     */
    protected boolean working = true;

    /**
     * Returns the time to wait for events before killing the background thread.
     * <p>
     * @return int
     */
    public int getWaitToDieMillis()
    {
        return waitToDieMillis;
    }

    /**
     * Sets the time to wait for events before killing the background thread.
     * <p>
     * @param wtdm the ms for the q to sit idle.
     */
    public void setWaitToDieMillis( int wtdm )
    {
        waitToDieMillis = wtdm;
    }

    /**
     * Creates a brief string identifying the listener and the region.
     * <p>
     * @return String debugging info.
     */
    @Override
    public String toString()
    {
        return "CacheEventQueue [listenerId=" + listenerId + ", cacheName=" + cacheName + "]";
    }

    /**
     * If they queue has an active thread it is considered alive.
     * <p>
     * @return The alive value
     */
    public synchronized boolean isAlive()
    {
        return ( !destroyed );
    }

    /**
     * Sets whether the queue is actively processing -- if there are working threads.
     * <p>
     * @param aState
     */
    public synchronized void setAlive( boolean aState )
    {
        destroyed = !aState;
    }

    /**
     * @return The listenerId value
     */
    public long getListenerId()
    {
        return listenerId;
    }

    /**
     * This adds a put event to the queue. When it is processed, the element will be put to the
     * listener.
     * <p>
     * @param ce The feature to be added to the PutEvent attribute
     * @exception IOException
     */
    public synchronized void addPutEvent( ICacheElement ce )
        throws IOException
    {
        if ( isWorking() )
        {
            put( new PutEvent( ce ) );
        }
        else
        {
            if ( log.isWarnEnabled() )
            {
                log.warn( "Not enqueuing Put Event for [" + this + "] because it's non-functional." );
            }
        }
    }

    /**
     * This adds a remove event to the queue. When processed the listener's remove method will be
     * called for the key.
     * <p>
     * @param key The feature to be added to the RemoveEvent attribute
     * @exception IOException
     */
    public synchronized void addRemoveEvent( Serializable key )
        throws IOException
    {
        if ( isWorking() )
        {
            put( new RemoveEvent( key ) );
        }
        else
        {
            if ( log.isWarnEnabled() )
            {
                log.warn( "Not enqueuing Remove Event for [" + this + "] because it's non-functional." );
            }
        }
    }

    /**
     * This adds a remove all event to the queue. When it is processed, all elements will be removed
     * from the cache.
     * <p>
     * @exception IOException
     */
    public synchronized void addRemoveAllEvent()
        throws IOException
    {
        if ( isWorking() )
        {
            put( new RemoveAllEvent() );
        }
        else
        {
            if ( log.isWarnEnabled() )
            {
                log.warn( "Not enqueuing RemoveAll Event for [" + this + "] because it's non-functional." );
            }
        }
    }

    /**
     * @exception IOException
     */
    public synchronized void addDisposeEvent()
        throws IOException
    {
        if ( isWorking() )
        {
            put( new DisposeEvent() );
        }
        else
        {
            if ( log.isWarnEnabled() )
            {
                log.warn( "Not enqueuing Dispose Event for [" + this + "] because it's non-functional." );
            }
        }
    }

    /**
     * Adds an event to the queue.
     * <p>
     * @param event
     */
    protected abstract void put( AbstractCacheEvent event );


    // /////////////////////////// Inner classes /////////////////////////////

    /** The queue is composed of nodes. */
    protected static class Node
    {
        /** Next node in the singly linked list. */
        Node next = null;

        /** The payload. */
        AbstractCacheEventQueue.AbstractCacheEvent event = null;
    }

    /**
     * Retries before declaring failure.
     * <p>
     * @author asmuts
     * @created January 15, 2002
     */
    protected abstract class AbstractCacheEvent
        implements Runnable
    {
        /** Number of failures encountered processing this event. */
        int failures = 0;

        /** Have we finished the job */
        boolean done = false;

        /**
         * Main processing method for the AbstractCacheEvent object
         */
        public void run()
        {
            try
            {
                doRun();
            }
            catch ( IOException e )
            {
                if ( log.isWarnEnabled() )
                {
                    log.warn( e );
                }
                if ( ++failures >= maxFailure )
                {
                    if ( log.isWarnEnabled() )
                    {
                        log.warn( "Error while running event from Queue: " + this
                            + ". Dropping Event and marking Event Queue as non-functional." );
                    }
                    setWorking( false );
                    setAlive( false );
                    return;
                }
                if ( log.isInfoEnabled() )
                {
                    log.info( "Error while running event from Queue: " + this + ". Retrying..." );
                }
                try
                {
                    Thread.sleep( waitBeforeRetry );
                    run();
                }
                catch ( InterruptedException ie )
                {
                    if ( log.isErrorEnabled() )
                    {
                        log.warn( "Interrupted while sleeping for retry on event " + this + "." );
                    }
                    // TODO consider if this is best. maybe we shoudl just
                    // destroy
                    setWorking( false );
                    setAlive( false );
                }
            }
        }

        /**
         * @exception IOException
         */
        protected abstract void doRun()
            throws IOException;
    }

    /**
     * An element should be put in the cache.
     * <p>
     * @author asmuts
     * @created January 15, 2002
     */
    protected class PutEvent
        extends AbstractCacheEvent
    {
        /** The element to put to the listener */
        private final ICacheElement ice;

        /**
         * Constructor for the PutEvent object.
         * <p>
         * @param ice
         * @exception IOException
         */
        PutEvent( ICacheElement ice )
            throws IOException
        {
            this.ice = ice;
        }

        /**
         * Call put on the listener.
         * <p>
         * @exception IOException
         */
        @Override
        protected void doRun()
            throws IOException
        {
            listener.handlePut( ice );
        }

        /**
         * For debugging.
         * <p>
         * @return Info on the key and value.
         */
        @Override
        public String toString()
        {
            return new StringBuffer( "PutEvent for key: " ).append( ice.getKey() ).append( " value: " )
                .append( ice.getVal() ).toString();
        }

    }

    /**
     * An element should be removed from the cache.
     * <p>
     * @author asmuts
     * @created January 15, 2002
     */
    protected class RemoveEvent
        extends AbstractCacheEvent
    {
        /** The key to remove from the listener */
        private final Serializable key;

        /**
         * Constructor for the RemoveEvent object
         * <p>
         * @param key
         * @exception IOException
         */
        RemoveEvent( Serializable key )
            throws IOException
        {
            this.key = key;
        }

        /**
         * Call remove on the listener.
         * <p>
         * @exception IOException
         */
        @Override
        protected void doRun()
            throws IOException
        {
            listener.handleRemove( cacheName, key );
        }

        /**
         * For debugging.
         * <p>
         * @return Info on the key to remove.
         */
        @Override
        public String toString()
        {
            return new StringBuffer( "RemoveEvent for " ).append( key ).toString();
        }

    }

    /**
     * All elements should be removed from the cache when this event is processed.
     * <p>
     * @author asmuts
     * @created January 15, 2002
     */
    protected class RemoveAllEvent
        extends AbstractCacheEvent
    {
        /**
         * Call removeAll on the listener.
         * <p>
         * @exception IOException
         */
        @Override
        protected void doRun()
            throws IOException
        {
            listener.handleRemoveAll( cacheName );
        }

        /**
         * For debugging.
         * <p>
         * @return The name of the event.
         */
        @Override
        public String toString()
        {
            return "RemoveAllEvent";
        }

    }

    /**
     * The cache should be disposed when this event is processed.
     * <p>
     * @author asmuts
     * @created January 15, 2002
     */
    protected class DisposeEvent
        extends AbstractCacheEvent
    {
        /**
         * Called when gets to the end of the queue
         * <p>
         * @exception IOException
         */
        @Override
        protected void doRun()
            throws IOException
        {
            listener.handleDispose( cacheName );
        }

        /**
         * For debugging.
         * <p>
         * @return The name of the event.
         */
        @Override
        public String toString()
        {
            return "DisposeEvent";
        }
    }

    /**
     * @return whether the queue is functional.
     */
    public boolean isWorking()
    {
        return working;
    }

    /**
     * This means that the queue is functional. If we reached the max number of failures, the queue
     * is marked as non functional and will never work again.
     * <p>
     * @param b
     */
    public void setWorking( boolean b )
    {
        working = b;
    }
}
