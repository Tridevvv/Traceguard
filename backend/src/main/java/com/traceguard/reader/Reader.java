package com.traceguard.reader;

/**
 * Hardware-independent RFID reader. {@link SimulatedReader} is used by default; a SerialReader for a
 * USB reader can implement this later without changing the rest of the system.
 */
public interface Reader {

    /** Returns the next tag reading, or null if nothing has been read. */
    ScanReading read();
}
