// ========================================================================
// $Id: AbstractReplicatedStore.java,v 1.5 2004/06/22 16:23:44 jules_gosnell Exp $
// Copyright 2002-2004 Mort Bay Consulting Pty. Ltd.
// ------------------------------------------------------------------------
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
// http://www.apache.org/licenses/LICENSE-2.0
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
// ========================================================================

package org.mortbay.j2ee.session;

//----------------------------------------

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.jboss.logging.Logger;

//----------------------------------------

// implement scavenging
// implement setMaxInactiveInterval
// look for NYI/TODO

// this infrastructure could probably be used across JMS aswell -
// think about it...

/**
 * Maintain synchronisation with other States representing the same session by
 * publishing changes made to ourself and updating ourself according to
 * notifications published by the other State objects.
 * 
 * @author <a href="mailto:jules@mortbay.com">Jules Gosnell</a>
 * @version 1.0
 */

abstract public class AbstractReplicatedStore extends AbstractStore
{
    protected final static Logger _log = Logger
            .getLogger(AbstractReplicatedStore.class);

    protected ClassLoader _loader;

    public AbstractReplicatedStore()
    {
        super();
        _loader = Thread.currentThread().getContextClassLoader();
    }

    public ClassLoader getLoader()
    {
        return _loader;
    }

    public void setLoader(ClassLoader loader)
    {
        _loader = loader;
    }

    // ----------------------------------------
    // tmp hack to prevent infinite loop
    private final static ThreadLocal _replicating = new ThreadLocal();

    public static boolean getReplicating()
    {
        return _replicating.get() == Boolean.TRUE;
    }

    public static void setReplicating(boolean replicating)
    {
        _replicating.set(replicating ? Boolean.TRUE : Boolean.FALSE);
    }

    // ----------------------------------------

    public Object clone()
    {
        AbstractReplicatedStore ars = (AbstractReplicatedStore) super.clone();
        ars.setLoader(getLoader());
        return ars;
    }

    protected Map _sessions = new HashMap();

    public int getSessions()
    {
        return _sessions.size();
    }

    // ----------------------------------------
    // Store API - Store LifeCycle

    public void destroy() // corresponds to ctor
    {
        _log.trace("destroying...");
        _sessions.clear();
        _sessions = null;
        setManager(null);
        super.destroy();
        _log.trace("...destroyed");
    }

    // ----------------------------------------
    // Store API - State LifeCycle

    public State newState(String id, int maxInactiveInterval) throws Exception
    {
        long creationTime = System.currentTimeMillis();

        if (!AbstractReplicatedStore.getReplicating())
        {
            Object[] argInstances = { id, new Long(creationTime),
                    new Integer(maxInactiveInterval),
                    new Integer(_actualMaxInactiveInterval) };
            publish(null, CREATE_SESSION, argInstances);
        }

        createSession(id, creationTime, maxInactiveInterval,
                _actualMaxInactiveInterval);

        // if we get one - all we have to do is loadState - because we
        // will have just created it...
        return loadState(id);
    }

    public State loadState(String id)
    {
        // pull it out of our cache - if it is not there, it doesn't
        // exist/hasn't been distributed...

        Object tmp;
        synchronized (_sessions)
        {
            tmp = _sessions.get(id);
        }
        return (State) tmp;
    }

    public void storeState(State state)
    {
        try
        {
            String id = state.getId();
            synchronized (_sessions)
            {
                _sessions.put(id, state);
            }
        }
        catch (Exception e)
        {
            _log.error("error storing session", e);
        }
    }

    public void removeState(State state) throws Exception
    {
        String id = state.getId();

        if (!AbstractReplicatedStore.getReplicating())
        {
            Object[] argInstances = { id };
            publish(null, DESTROY_SESSION, argInstances);
        }

        destroySession(id);
    }

    // ----------------------------------------
    // Store API - garbage collection

    public void scavenge() throws Exception
    {
        _log.trace("starting distributed scavenging...");
        Collection copy;
        synchronized (_sessions)
        {
            copy = new ArrayList(_sessions.values());
        }
        if (_log.isTraceEnabled())
        {
            int n;
            synchronized (_subscribers)
            {
                n = _subscribers.size();
            }
            _log.trace(copy.size() + " distributed sessions, " + n
                    + " subscribers");
        }
        int n = 0;
        for (Iterator i = copy.iterator(); i.hasNext();)
        {
            LocalState state = (LocalState) i.next();
            if (!state.isValid(_scavengerExtraTime))
            {
                String id = state.getId();
                if (_log.isDebugEnabled())
                    _log.debug("scavenging distributed session " + id);
                destroySession(id);
                i.remove();
                ++n;
            }
        }
        if (_log.isTraceEnabled())
            _log.trace("scavenged " + n + " distributed sessions");
        _log.trace("...finished distributed scavenging");
    }

    // ----------------------------------------
    // Store API - hacks... - NYI/TODO

    public void passivateSession(StateAdaptor sa)
    {
    }

    public boolean isDistributed()
    {
        return true;
    }

    // ----------------------------------------
    // utils

    public String getContextPath()
    {
        return getManager().getContextPath();
    }

    // ----------------------------------------
    // change notification API

    protected static Map _methodToInteger = new HashMap();

    protected static Method[] _integerToMethod = new Method[8];

    protected static Method CREATE_SESSION;

    protected static Method DESTROY_SESSION;

    protected static Method TOUCH_SESSIONS;

    protected static Method SET_LAST_ACCESSED_TIME;

    static
    {
        // this is absolutely horrible and will break if anyone changes
        // the shape of the interface - but it is a quick, easy and
        // efficient hack - so I am using it while I think of a better
        // way...
        try
        {
            int index = 0;
            Method m = null;

            // class methods...
            m = CREATE_SESSION = AbstractReplicatedStore.class.getMethod(
                    "createSession", new Class[] { String.class, Long.TYPE,
                            Integer.TYPE, Integer.TYPE });
            _integerToMethod[index] = m;
            _methodToInteger.put(m.getName(), new Integer(index));
            index++;

            m = DESTROY_SESSION = AbstractReplicatedStore.class.getMethod(
                    "destroySession", new Class[] { String.class });
            _integerToMethod[index] = m;
            _methodToInteger.put(m.getName(), new Integer(index));
            index++;

            m = TOUCH_SESSIONS = AbstractReplicatedStore.class.getMethod(
                    "touchSessions", new Class[] { String[].class, Long.TYPE });
            _integerToMethod[index] = m;
            _methodToInteger.put(m.getName(), new Integer(index));
            index++;

            // instance methods...
            m = SET_LAST_ACCESSED_TIME = State.class.getMethod(
                    "setLastAccessedTime", new Class[] { Long.TYPE });
            _integerToMethod[index] = m;
            _methodToInteger.put(m.getName(), new Integer(index));
            index++;

            m = State.class.getMethod("setMaxInactiveInterval",
                    new Class[] { Integer.TYPE });
            _integerToMethod[index] = m;
            _methodToInteger.put(m.getName(), new Integer(index));
            index++;

            m = State.class.getMethod("setAttribute", new Class[] {
                    String.class, Object.class, Boolean.TYPE });
            _integerToMethod[index] = m;
            _methodToInteger.put(m.getName(), new Integer(index));
            index++;

            m = State.class.getMethod("setAttributes",
                    new Class[] { Map.class });
            _integerToMethod[index] = m;
            _methodToInteger.put(m.getName(), new Integer(index));
            index++;

            m = State.class.getMethod("removeAttribute", new Class[] {
                    String.class, Boolean.TYPE });
            _integerToMethod[index] = m;
            _methodToInteger.put(m.getName(), new Integer(index));
            index++;
        }
        catch (Exception e)
        {
            System.err
                    .println("AbstractReplicatedStore: something went wrong building dispatch tables");
            e.printStackTrace(System.err);
        }
    }

    abstract protected void publish(String id, Method method,
            Object[] argInstances);

    protected void dispatch(String id, Integer methodId, Object[] argInstances)
    {
        try
        {
            AbstractReplicatedStore.setReplicating(true);

            Object target = null;
            if (id == null)
            {
                // either this is a class method
                target = this;
            }
            else
            {
                // or an instance method..
                synchronized (_subscribers)
                {
                    target = _subscribers.get(id);
                }
            }

            try
            {
                Method method = _integerToMethod[methodId.intValue()];
                if (target == null)
                    _log.warn("null target for " + method);
                else
                    method.invoke(target, argInstances);
            }
            catch (Exception e)
            {
                _log
                        .error(
                                "this should never happen - code version mismatch ?",
                                e);
            }
        }
        finally
        {
            AbstractReplicatedStore.setReplicating(false);
        }
    }

    public void createSession(String id, long creationTime,
            int maxInactiveInterval, int actualMaxInactiveInterval)
    {
        if (_log.isTraceEnabled())
            _log.trace("creating replicated session: " + id);
        State state = new LocalState(id, creationTime, maxInactiveInterval,
                actualMaxInactiveInterval);
        synchronized (_sessions)
        {
            _sessions.put(id, state);
        }

        if (AbstractReplicatedStore.getReplicating())
        {
            //	_log.info("trying to promote replicated session");
            getManager().getHttpSession(id); // should cause creation of corresponding InterceptorStack
        }
    }

    public void destroySession(String id)
    {
        if (_log.isTraceEnabled())
            _log.trace("destroying replicated session: " + id);
        if (getManager().sessionExists(id))
            getManager().destroySession(getManager().getHttpSession(id));
        synchronized (_sessions)
        {
            _sessions.remove(id);
        }
    }

    public void touchSessions(String[] ids, long time)
    {
        //      _log.info("touching sessions...: "+ids);
        for (int i = 0; i < ids.length; i++)
        {
            String id = ids[i];
            Object target;
            // I could synch the whole block. This is slower, but will not
            // hold up everything else...
            synchronized (_subscribers)
            {
                target = _subscribers.get(id);
            }
            try
            {
                ((StateInterceptor) target).setLastAccessedTime(time);
            }
            catch (Exception e)
            {
                _log.warn("unable to touch session: " + id
                        + " probably already removed");
            }
        }
    }

    //----------------------------------------
    // subscription - Listener management...

    protected Map _subscribers = new HashMap();

    public void subscribe(String id, Object o)
    {
        _log.trace("subscribing: " + id);
        synchronized (_subscribers)
        {
            _subscribers.put(id, o);
        }
    }

    public void unsubscribe(String id)
    {
        _log.trace("unsubscribing: " + id);
        synchronized (_subscribers)
        {
            _subscribers.remove(id);
        }
    }
}
