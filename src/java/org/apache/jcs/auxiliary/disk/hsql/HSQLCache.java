package org.apache.jcs.auxiliary.disk.hsql;

/*
 * The Apache Software License, Version 1.1
 *
 * Copyright (c) 2001 The Apache Software Foundation.  All rights
 * reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in
 *    the documentation and/or other materials provided with the
 *    distribution.
 *
 * 3. The end-user documentation included with the redistribution, if
 *    any, must include the following acknowlegement:
 *       "This product includes software developed by the
 *        Apache Software Foundation (http://www.apache.org/)."
 *    Alternately, this acknowlegement may appear in the software itself,
 *    if and wherever such third-party acknowlegements normally appear.
 *
 * 4. The names "The Jakarta Project", "Velocity", and "Apache Software
 *    Foundation" must not be used to endorse or promote products derived
 *    from this software without prior written permission. For written
 *    permission, please contact apache@apache.org.
 *
 * 5. Products derived from this software may not be called "Apache"
 *    nor may "Apache" appear in their names without prior written
 *    permission of the Apache Group.
 *
 * THIS SOFTWARE IS PROVIDED ``AS IS'' AND ANY EXPRESSED OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED.  IN NO EVENT SHALL THE APACHE SOFTWARE FOUNDATION OR
 * ITS CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF
 * USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT
 * OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF
 * SUCH DAMAGE.
 * ====================================================================
 *
 * This software consists of voluntary contributions made by many
 * individuals on behalf of the Apache Software Foundation.  For more
 * information on the Apache Software Foundation, please see
 * <http://www.apache.org/>.
 */

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.jcs.auxiliary.disk.AbstractDiskCache;
import org.apache.jcs.engine.CacheConstants;
import org.apache.jcs.engine.behavior.ICacheElement;
import org.apache.jcs.utils.data.PropertyGroups;

/**
 * HSQLDB Based Local Persistence.
 *
 * <b>VERY EXPERIMENTAL, and only partially implemented</b>
 *
 * @author Aaron Smuts
 * @created January 15, 2002
 * @version 1.0
 */
public class HSQLCache extends AbstractDiskCache
{
    private final static Log log =
        LogFactory.getLog( HSQLCache.class );

    private int numInstances = 0;

    public boolean isAlive = false;

    HSQLCacheAttributes cattr;

    // for now use one statement per cache and keep it open
    // can move up to manager level or implement pooling if there are too many
    // caches
    Connection cConn;
    Statement sStatement;

    /**
     * Constructor for the HSQLCache object
     *
     * @param cattr
     */
    public HSQLCache( HSQLCacheAttributes cattr )
    {
        super( cattr.getCacheName() );

        this.cattr = cattr;

        String rafroot = cattr.getDiskPath();

        numInstances++;

        //String rafroot = cattr.getDiskPath();
        if ( rafroot == null )
        {
            try
            {
                PropertyGroups pg = new PropertyGroups( "/cache.properties" );
                rafroot = pg.getProperty( "diskPath" );
            }
            catch ( Exception e )
            {
                log.error( e );
            }
        }

        try
        {

            Properties p = new Properties();
            String driver = p.getProperty( "driver", "org.hsqldb.jdbcDriver" );
            String url = p.getProperty( "url", "jdbc:hsqldb:" );
            String database = p.getProperty( "database", "cache_hsql_db" );
            String user = p.getProperty( "user", "sa" );
            String password = p.getProperty( "password", "" );
            boolean test = p.getProperty( "test", "true" ).equalsIgnoreCase( "true" );
            // boolean log = p.getProperty( "log", "true" ).equalsIgnoreCase( "true" );

            try
            {
                if ( log.isDebugEnabled() )
                {
                    log.debug( "driver  =" + driver
                               + ", url = " + url
                               + ", database = " + database
                               + ", user = " + user
                               + ", password = " + password
                               + ", test = " + test );
                }

                // As described in the JDBC FAQ:
                // http://java.sun.com/products/jdbc/jdbc-frequent.html;
                // Why doesn't calling class.forName() load my JDBC driver?
                // There is a bug in the JDK 1.1.x that can cause Class.forName() to fail.
                new org.hsqldb.jdbcDriver();
                Class.forName( driver ).newInstance();

                cConn = DriverManager.getConnection( url + database, user,
                                                     password );

                try
                {
                    sStatement = cConn.createStatement();
                    isAlive = true;
                }
                catch ( SQLException e )
                {
                    System.out.println( "Exception: " + e );
                    isAlive = false;
                }

                setupTABLE();

            }
            catch ( Exception e )
            {
                log.error( "QueryTool.init", e );
            }

        }
        catch ( Exception e )
        {
            log.error( e );
        }

    }
    // end constructor

    /** SETUP TABLE FOR CACHE */
    void setupTABLE()
    {

        boolean newT = true;

        String setup = "create table " + cacheName
            + " (KEY varchar(255) primary key, ELEMENT binary)";

        try
        {
            sStatement.executeQuery( setup );
        }
        catch ( SQLException e )
        {
            if ( e.toString().indexOf( "already exists" ) != -1 )
            {
                newT = false;
            }
            log.error( e );
        }

        String setupData[] = {
            "create index iKEY on " + cacheName + " (KEY)"
        };

        if ( newT )
        {
            for ( int i = 1; i < setupData.length; i++ )
            {
                try
                {
                    sStatement.executeQuery( setupData[ i ] );
                }
                catch ( SQLException e )
                {
                    System.out.println( "Exception: " + e );
                }
            }
        }
        // end ifnew

    }

    /** Description of the Method */
    public void doUpdate( ICacheElement ce )
    {
        log.debug( "update" );

        if ( !isAlive )
        {
            return;
        }

        if ( log.isDebugEnabled() )
        {
            log.debug( "Putting " + ce.getKey() + " on disk." );
        }

        byte[] element;

        try
        {
            element = serialize( ce );
        }
        catch ( IOException e )
        {
            log.error( "Could not serialize element", e );

            return;
        }

        boolean exists = false;

        // First do a query to determine if the element already exists

        try
        {
            String sqlS = "SELECT element FROM " + cacheName
                          + " WHERE key = '" + ( String ) ce.getKey() + "'";

            ResultSet rs = sStatement.executeQuery( sqlS );

            if ( rs.next() )
            {
                exists = true;
            }

            rs.close();
        }
        catch ( SQLException e )
        {
            log.error( e );
        }

        // If it doesn't exist, insert it, otherwise update

        if ( !exists )
        {
            try
            {
                String sqlI = "insert into " + cacheName
                    + " (KEY, ELEMENT) values (?, ? )";

                PreparedStatement psInsert = cConn.prepareStatement( sqlI );
                psInsert.setString( 1, ( String ) ce.getKey() );
                psInsert.setBytes( 2, element );
                psInsert.execute();
                psInsert.close();
                //sStatement.executeUpdate(sql);
            }
            catch ( SQLException e )
            {
                if ( e.toString().indexOf( "Violation of unique index" ) != -1
                     || e.getMessage().indexOf( "Violation of unique index" ) != -1 )
                {
                    exists = true;
                }
                else
                {
                    log.error( "Could not insert element", e );
                }
            }
        }
        else
        {
            try
            {
                String sqlU = "update " + cacheName + " set ELEMENT  = ? ";
                PreparedStatement psUpdate = cConn.prepareStatement( sqlU );
                psUpdate.setBytes( 1, element );
                psUpdate.setString( 2, ( String ) ce.getKey() );
                psUpdate.execute();
                psUpdate.close();

                log.debug( "ran update" );
            }
            catch ( SQLException e2 )
            {
                log.error( "e2 Exception: " + e2 );
            }

        }
    }

    /** Description of the Method */
    public ICacheElement doGet( Serializable key )
    {
        if ( log.isDebugEnabled() )
        {
            log.debug( "getting " + key + " from disk" );
        }

        if ( !isAlive )
        {
            return null;
        }

        ICacheElement obj = null;

        byte[] data = null;
        try
        {
            String sqlS = "select ELEMENT from " + cacheName + " where KEY = ?";
            PreparedStatement psSelect = cConn.prepareStatement( sqlS );
            psSelect.setString( 1, ( String ) key );
            ResultSet rs = psSelect.executeQuery();
            if ( rs.next() )
            {
                data = rs.getBytes( 1 );
            }
            if ( data != null )
            {
                try
                {
                    ByteArrayInputStream bais = new ByteArrayInputStream( data );
                    BufferedInputStream bis = new BufferedInputStream( bais );
                    ObjectInputStream ois = new ObjectInputStream( bis );
                    try
                    {
                        obj = ( ICacheElement ) ois.readObject();
                    }
                    finally
                    {
                        ois.close();
                    }
                    // move to finally
                    rs.close();
                    psSelect.close();
                }
                catch ( IOException ioe )
                {
                    log.error( ioe );
                }
                catch ( Exception e )
                {
                    log.error( e );
                }

            }
            //else {
            //return null;
            //}

        }
        catch ( SQLException sqle )
        {
            log.error( sqle );
        }

        return obj;
    }

    /**
     * Returns true if the removal was succesful; or false if there is nothing
     * to remove. Current implementation always result in a disk orphan.
     */
    public boolean doRemove( Serializable key )
    {
        // remove single item.
        String sql = "delete from " + cacheName + " where KEY = '" + key + "'";

        try
        {
            if ( key instanceof String && key.toString().endsWith( CacheConstants.NAME_COMPONENT_DELIMITER ) )
            {
                // remove all keys of the same name group.
                sql = "delete from " + cacheName + " where KEY = like '" + key + "%'";
            }

            try
            {
                sStatement.executeQuery( sql );
            }
            catch ( SQLException e )
            {
                log.error( e );
            }

        }
        catch ( Exception e )
        {
            log.error( e );
            reset();
        }
        return false;
    }

    /** Description of the Method */
    public void doRemoveAll()
    {
        try
        {
            //reset();
        }
        catch ( RuntimeException e )
        {
            log.error( e );
            //reset();
        }
    }

    // handle error by last resort, force content update, or removeall
    /** Description of the Method */
    public void reset()
    {

    }

    /** Description of the Method */
    public void doDispose()
    {

    }

    /**
     * Returns the current cache size.
     *
     * @return The size value
     */
    public int getSize()
    {
        return 0;
        // need to get count
    }

    /**
     * Returns the serialized form of the given object in a byte array.
     */
    static byte[] serialize( Serializable obj )
        throws IOException
    {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream( baos );
        try
        {
            oos.writeObject( obj );
        }
        finally
        {
            oos.close();
        }
        return baos.toByteArray();
    }

}

