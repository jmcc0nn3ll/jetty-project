// ========================================================================
// $Id$
// Copyright 2006 Mort Bay Consulting Pty. Ltd.
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

package org.mortbay.jetty.plus.naming;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.LinkRef;
import javax.naming.NamingException;
import javax.transaction.UserTransaction;

import org.mortbay.log.Log;
import org.mortbay.naming.NamingUtil;

/**
 * Transaction
 *
 * Class to represent a JTA UserTransaction impl.
 * 
 * 
 */
/**
 * Transaction
 *
 *
 */
public class Transaction extends NamingEntry
{
    public static final String USER_TRANSACTION = "UserTransaction";
    

    public static void bindToENC ()
    throws NamingException
    {
        Transaction txEntry = (Transaction)NamingEntryUtil.lookupNamingEntry(Transaction.USER_TRANSACTION);
        txEntry.bindToComp();
    }
 
    
    
    public Transaction (UserTransaction userTransaction)
    throws NamingException
    {
        super (USER_TRANSACTION, userTransaction);           
    }
    
    
    /** 
     * Allow other bindings of UserTransaction.
     * 
     * These should be in ADDITION to java:comp/UserTransaction
     * @see org.mortbay.jetty.plus.naming.NamingEntry#bindToENC(java.lang.String)
     */
    public void bindToENC (String localName)
    throws NamingException
    {   
        InitialContext ic = new InitialContext();
        Context env = (Context)ic.lookup("java:comp/env");
        Log.debug("Binding java:comp/env"+getJndiName()+" to "+absoluteObjectNameString);
        NamingUtil.bind(env, localName, new LinkRef(absoluteObjectNameString));
    }
    
    /**
     * Insist on the java:comp/UserTransaction binding
     * @throws NamingException
     */
    private void bindToComp ()
    throws NamingException
    {   
        //ignore the name, it is always bound to java:comp
        InitialContext ic = new InitialContext();
        Context env = (Context)ic.lookup("java:comp");
        Log.debug("Binding java:comp/"+getJndiName()+" to "+absoluteObjectNameString);
        NamingUtil.bind(env, getJndiName(), new LinkRef(absoluteObjectNameString));
    }
    
    /**
     * Unbind this Transaction from a java:comp
     */
    public void unbindENC ()
    {
        try
        {
            InitialContext ic = new InitialContext();
            Context env = (Context)ic.lookup("java:comp");
            Log.debug("Unbinding java:comp/"+getJndiName());
            env.unbind(getJndiName());
        }
        catch (NamingException e)
        {
            Log.warn(e);
        }
    }
}
