package org.apache.jcs.engine.behavior;

import java.io.IOException;
import java.io.Serializable;

/**
 * This is the top level interface for all cache like structures. It defines
 * the methods used internally by JCS to access, modify, and instrument such
 * structures. This allows for a suite of reusable components for accessing
 * such structures, for example asynchronous access via an event queue.
 *
 * @author <a href="mailto:asmuts@yahoo.com">Aaron Smuts</a>
 * @author <a href="mailto:jtaylor@apache.org">James Taylor</a>
 * @version $Id$
 */
public interface ICache extends ICacheType
{
    /** Puts an item to the cache. */
    public void update( ICacheElement ce ) throws IOException;

    /** Gets an item from the cache. */
    public ICacheElement get( Serializable key ) throws IOException;

    /** Removes an item from the cache. */
    public boolean remove( Serializable key ) throws IOException;

    /** Removes all cached items from the cache. */
    public void removeAll() throws IOException;

    /** Prepares for shutdown. */
    public void dispose() throws IOException;

    /** Returns the current cache size. */
    public int getSize();

    /** Returns the cache status. */
    public int getStatus();

    /** Returns the cache name. */
    public String getCacheName();
}