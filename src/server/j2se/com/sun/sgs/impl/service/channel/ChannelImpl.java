package com.sun.sgs.impl.service.channel;

import com.sun.sgs.app.AppContext;
import com.sun.sgs.app.Channel;
import com.sun.sgs.app.ChannelListener;
import com.sun.sgs.app.ChannelManager;
import com.sun.sgs.app.ClientSession;
import com.sun.sgs.app.Delivery;
import com.sun.sgs.impl.service.session.MessageBuffer;
import com.sun.sgs.impl.service.session.SgsProtocol;
import com.sun.sgs.impl.util.LoggerWrapper;
import com.sun.sgs.kernel.KernelRunnable;
import com.sun.sgs.service.SgsClientSession;
import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Channel implementation for use within a single transaction
 * specified by the context passed during construction.
 */
final class ChannelImpl implements Channel, Serializable {

    /** The logger for this class. */
    private final static LoggerWrapper logger =
	new LoggerWrapper(
	    Logger.getLogger(ChannelImpl.class.getName()));

    /** Transaction-related context information. */
    private final Context context;

    /** Persistent channel state. */
    private final ChannelState state;

    /** Flag that is 'true' if this channel is closed. */
    private boolean channelClosed = false;

    /**
     * Constructs an instance of this class with the specified context
     * and channel state.
     *
     * @param context a context
     * @param state a channel state
     */
    ChannelImpl(Context context, ChannelState state) {
	if (logger.isLoggable(Level.FINER)) {
	    logger.log(Level.FINER, "Created ChannelImpl context:{0} state:{1}",
		       context, state);
	}
	this.state =  state;
	this.context = context;
    }

    /* -- Implement Channel -- */
    
    /** {@inheritDoc} */
    public String getName() {
	checkContext();
	if (logger.isLoggable(Level.FINEST)) {
	    logger.log(Level.FINEST, "getName returns {0}", state.name);
	}
	return state.name;
    }

    /** {@inheritDoc} */
    public Delivery getDeliveryRequirement() {
	checkContext();
	if (logger.isLoggable(Level.FINEST)) {
	    logger.log(Level.FINEST,
		       "getDeliveryRequirement returns {0}", state.delivery);
	}
	return state.delivery;
    }

    /** {@inheritDoc} */
    public void join(final ClientSession session, ChannelListener listener) {
	try {
	    checkClosed();
	    if (session == null) {
		throw new NullPointerException("null session");
	    }
	    if (listener != null && !(listener instanceof Serializable)) {
		throw new IllegalArgumentException("listener not serializable");
	    }
	    if (!(session instanceof SgsClientSession)) {
		if (logger.isLoggable(Level.SEVERE)) {
		    logger.log(
			Level.SEVERE,
			"join: session does not implement" +
			"SgsClientSession:{0}", session);
		}
		throw new IllegalArgumentException(
		    "unexpected ClientSession type: " + session);
	    }
	    if (state.sessions.get(session) == null) {
		context.dataService.markForUpdate(state);
		state.setListener(session, listener);
	    }
	    
	    submitNonTransactionalTask(new KernelRunnable() {
		public void run() {
		    int nameSize = MessageBuffer.getSize(state.name);
		    MessageBuffer buf = new MessageBuffer(3 + nameSize);
		    buf.putByte(SgsProtocol.VERSION).
			putByte(SgsProtocol.CHANNEL_SERVICE).
			putByte(SgsProtocol.CHANNEL_JOIN).
			putString(state.name);
		    sendProtocolMessage(session, buf.getBuffer());
		}});
	    
	    if (logger.isLoggable(Level.FINEST)) {
		logger.log(Level.FINEST, "join session:{0} returns", session);
	    }
	    
	} catch (RuntimeException e) {
	    if (logger.isLoggable(Level.FINEST)) {
		logger.logThrow(Level.FINEST, e, "leave throws");
	    }
	    throw e;
	}
    }

    /** {@inheritDoc} */
    public void leave(final ClientSession session) {
	try {
	    checkClosed();
	    if (session == null) {
		throw new NullPointerException("null client session");
	    }
	    if (!(session instanceof SgsClientSession)) {
		if (logger.isLoggable(Level.SEVERE)) {
		    logger.log(
			Level.SEVERE,
			"join: session does not implement " +
			"SgsClientSession:{0}", session);
		}
		throw new IllegalArgumentException(
		    "unexpected ClientSession type: " + session);
	    }
	    if (state.sessions.get(session) != null) {
		context.dataService.markForUpdate(state);
		state.sessions.remove(session);
	    }
	
	    submitNonTransactionalTask(new KernelRunnable() {
		public void run() {
		    int nameSize = MessageBuffer.getSize(state.name);
		    MessageBuffer buf = new MessageBuffer(3 + nameSize);
		    buf.putByte(SgsProtocol.VERSION).
			putByte(SgsProtocol.CHANNEL_SERVICE).
			putByte(SgsProtocol.CHANNEL_LEAVE).
			putString(state.name);
		    sendProtocolMessage(session, buf.getBuffer());
		}});
	
	    if (logger.isLoggable(Level.FINEST)) {
		logger.log(Level.FINEST, "leave session:{0} returns", session);
	    }
	    
	} catch (RuntimeException e) {
	    if (logger.isLoggable(Level.FINEST)) {
		logger.logThrow(Level.FINEST, e, "leave throws");
	    }
	    throw e;
	}
    }

    /** {@inheritDoc} */
    public void leaveAll() {
	try {
	    checkClosed();
	    if (!state.sessions.isEmpty()) {
		context.dataService.markForUpdate(state);
		state.sessions.clear();
	    }

	    final Collection<ClientSession> sessions = getSessions();

	    submitNonTransactionalTask(new KernelRunnable() {
		public void run() {
		    int nameSize = MessageBuffer.getSize(state.name);
		    MessageBuffer buf = new MessageBuffer(3 + nameSize);
		    buf.putByte(SgsProtocol.VERSION).
			putByte(SgsProtocol.CHANNEL_SERVICE).
			putByte(SgsProtocol.CHANNEL_LEAVE).
			putString(state.name);
		    byte[] message = buf.getBuffer();
		    
		    for (ClientSession session : sessions) {
			sendProtocolMessage(session, message);
		    }
		}});
	    
	    if (logger.isLoggable(Level.FINEST)) {
		logger.log(Level.FINEST, "leaveAll returns");
	    }
	} catch (RuntimeException e) {
	    if (logger.isLoggable(Level.FINEST)) {
		logger.logThrow(Level.FINEST, e, "leave throws");
	    }
	    throw e;
	}
    }

    /** {@inheritDoc} */
    public boolean hasSessions() {
	checkClosed();
	boolean hasSessions = !state.sessions.isEmpty();
	if (logger.isLoggable(Level.FINEST)) {
	    logger.log(Level.FINEST, "hasSessions returns {0}", hasSessions);
	}
	return hasSessions;
    }

    /** {@inheritDoc} */
    public Collection<ClientSession> getSessions() {
	checkClosed();
	Collection<ClientSession> sessions =
	    Collections.unmodifiableCollection(state.sessions.keySet());	
	if (logger.isLoggable(Level.FINEST)) {
	    logger.log(Level.FINEST, "getSessions returns {0}", sessions);
	}
	return sessions;
    }

    /** {@inheritDoc} */
    public void send(byte[] message) {
	checkClosed();
	if (message == null) {
	    throw new NullPointerException("null message");
	}
	submitNonTransactionalTask(
	    new SendTask(state.getSessions(), message));
    }

    /** {@inheritDoc} */
    public void send(ClientSession recipient, byte[] message) { 
	checkClosed();
	if (recipient == null) {
	    throw new NullPointerException("null recipient");
	} else if (message == null) {
	    throw new NullPointerException("null message");
	}
	
	Collection<ClientSession> sessions = new ArrayList<ClientSession>();
	sessions.add(recipient);
	submitNonTransactionalTask(new SendTask(sessions, message));
    }

    /** {@inheritDoc} */
    public void send(Collection<ClientSession> recipients,
		     byte[] message)
    {
	checkClosed();
	if (recipients == null) {
	    throw new NullPointerException("null recipients");
	} else if (message == null) {
	    throw new NullPointerException("null message");
	}

	if (recipients.isEmpty()) {
	    return;
	}

	submitNonTransactionalTask(new SendTask(recipients, message));
    }

    /** {@inheritDoc} */
    public void close() {
	checkContext();
	channelClosed = true;
	context.removeChannel(state.name);
	if (logger.isLoggable(Level.FINEST)) {
	    logger.log(Level.FINEST, "close returns");
	}
    }

    /* -- Implement Object -- */

    /** {@inheritDoc} */
    public boolean equals(Object obj) {
	return
	    (this == obj) ||
	    (obj.getClass() == this.getClass() &&
	     state.equals(((ChannelImpl) obj).state));
    }

    /** {@inheritDoc} */
    public int hashCode() {
	return state.name.hashCode();
    }

    /** {@inheritDoc} */
    public String toString() {
	return getClass().getName() + "[" + state.name + "]";
    }

    /* -- Serialization methods -- */

    private Object writeReplace() {
	return new External(state.name);
    }

    /**
     * Represents the persistent represntation for a channel (just its name).
     */
    private final static class External implements Serializable {

	private final static long serialVersionUID = 1L;

	private final String name;

	External(String name) {
	    this.name = name;
	}

	private void writeObject(ObjectOutputStream out) throws IOException {
	    out.defaultWriteObject();
	}

	private void readObject(ObjectInputStream in)
	    throws IOException, ClassNotFoundException
	{
	    in.defaultReadObject();
	}

	private Object readResolve() throws ObjectStreamException {
	    try {
		ChannelManager cm = AppContext.getChannelManager();
		Channel channel = cm.getChannel(name);
		return channel;
	    } catch (RuntimeException e) {
		throw (InvalidObjectException)
		    new InvalidObjectException(e.getMessage()).initCause(e);
	    }
	}
    }

    /* -- other methods and classes -- */

    /**
     * Checks that this channel's context is currently active,
     * throwing TransactionNotActiveException if it isn't.
     */
    private void checkContext() {
	ChannelServiceImpl.checkContext(context);
    }

    /**
     * Checks the context, and then checks that this channel is not
     * closed, throwing an IllegalStateException if the channel is
     * closed.
     */
    private void checkClosed() {
	checkContext();
	if (channelClosed) {
	    throw new IllegalStateException("channel is closed");
	}
    }

    /**
     * Submits a non-durable, non-transactional task.
     */
    private void submitNonTransactionalTask(KernelRunnable task) {
	context.taskService.scheduleNonDurableTask(task);
    }

    /**
     * Send a protocol message to the specified session, logging (but
     * not throwing) any exception.
     */
    private void sendProtocolMessage(ClientSession session, byte[] message) {
	try {
	    ((SgsClientSession) session).sendMessage(message, state.delivery);
	} catch (RuntimeException e) {
	    if (logger.isLoggable(Level.FINEST)) {
		logger.logThrow(
		    Level.FINEST, e,
		    "sendProtcolMessage session:{0} message:{1} throws",
		    session, message);
	    }
	    // eat exception
	}
    }
    
    /**
     * Task for sending a message to a set of clients.
     */
    private final class SendTask implements KernelRunnable {

	private final Collection<byte[]> clients = new ArrayList<byte[]>();
	private final byte[] message;

	SendTask(Collection<ClientSession> sessions, byte[] message) {
	    for (ClientSession session : sessions) {
		this.clients.add(session.getSessionId());
	    }
	    this.message = message;
	}

	public void run() {
	    MessageBuffer buf = new MessageBuffer(5 + message.length);
	    buf.putByte(SgsProtocol.VERSION).
		putByte(SgsProtocol.CHANNEL_SERVICE).
		putByte(SgsProtocol.MESSAGE_SEND).
		putShort(message.length).
		putBytes(message);
	    
	    for (byte[] sessionId : clients) {
		SgsClientSession session = 
		    context.sessionService.getClientSession(sessionId);
		if (session != null && session.isConnected()) {
		    session.sendMessage(message, state.delivery);
		}
	    }
	}
    }
}
