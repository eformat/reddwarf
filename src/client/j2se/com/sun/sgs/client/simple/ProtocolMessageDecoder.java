package com.sun.sgs.client.simple;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;

/**
 * Convenience class for translating protocol messages from bytes into their 
 * component parts.
 * 
 * @author      Sten Anderson
 * @version     1.0
 */
class ProtocolMessageDecoder {
    
    private DataInputStream inputStream;
    
    ProtocolMessageDecoder(byte[] message) {
        inputStream = new DataInputStream(new ByteArrayInputStream(message));
    }
    
    /**
     * Reads a String from the byte stream as Modified UTF-8 at the current
     * position.
     * 
     * @return a Modified UTF-8 string
     */
    String readString() {
        String str = null;
        try {
            str = inputStream.readUTF();
        }
        catch (IOException ioe) {
            // not thrown by the input stream
        }
        
        return str;

    }
    
    /**
     * Reads the next int off the byte stream and returns true if it equals
     * 1, otherwise false.
     * 
     * @return true if the int read equals one.
     */
    boolean readBoolean() {
        boolean b = false;
        try {
            b = inputStream.readBoolean();
        }
        catch (IOException ioe) {
            // not thrown by the input stream
        }
        return b;
    }

    /**
     * Reads the next 8 bytes off the byte stream at the current position
     * and returns a network byte ordered long.
     * 
     * @return the next 8 bytes as a long
     */
    long readLong() {
        long num = 0;
        try {
            num = inputStream.readLong();
        }
        catch (IOException ioe) {
            // not thrown by the input stream
        }
        return num;
    }
    
    /**
     * Reads the next 4 bytes from the byte stream at the current position
     * and returns a network byte ordered int. 
     * 
     * @return the next 4 bytes as an int
     */
    int readInt() {
        int num = 0;
        try {
            num = inputStream.readInt();
        }
        catch (IOException ioe) {
            // not thrown by the input stream
        }
        return num;
    }
    
    /**
     * Reads the next 2 bytes from the byte stream at the current position
     * and returns a network byte ordered short.
     * 
     * @return the next 2 bytes as a short
     */
    short readShort() {
        short num = 0;
        try {
            num = inputStream.readShort();
        }
        catch (IOException ioe) {
            // not thrown by the input stream
        }
        return num; 
    }

    /**
     * Reads the next short from the input stream, interpreting it as a
     * size, and reads that many more bytes from the stream, returning
     * the resulting byte array.
     * 
     * @return a byte array matching the length of the first short read
     * from the stream
     */
    byte[] readBytes() {
        byte[] bytes = null;
        
        try {
            bytes = new byte[inputStream.readShort()];
            inputStream.read(bytes);
        }
        catch (IOException ioe) {
            // not thrown by the input stream 
        }
        
        return bytes;
    }
    
    /**
     * Reads a Java signed byte off the buffer and converts it to an unsigned 
     * one (0-255).
     * 
     * @return the unsigned representation of the next byte off the
     * buffer (as an int).
     */
    int readUnsignedByte() {
        int uByte = 0;
        
        try {
            uByte = inputStream.read() &  0xff;
        }
        catch (IOException ioe) {
            // not thrown by the input stream
        }
        
        return uByte;
    }
    
    /**
     * Convenience method for reading the first byte as the version number.
     * 
     * @return the first byte as the version number of the protocol.
     */
    int readVersionNumber() {
        return readUnsignedByte();
    }
    
    /**
     * Convenience method for reading the third byte as the command op code.
     * 
     * @return the third byte as the command code of the message.
     */
    int readCommand() {
        return readUnsignedByte();
    }

    /**
     * Convenience method for reading the second byte as the service number.
     * 
     * @return the second byte as the service number.
     */
    int readServiceNumber() {
        return readUnsignedByte();
    }

}
