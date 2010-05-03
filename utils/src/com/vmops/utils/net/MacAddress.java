/**
 *  Copyright (C) 2010 VMOps, Inc.  All rights reserved.
 * 
 * This software is licensed under the GNU General Public License v3 or later.  
 * 
 * It is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * 
 */

package com.vmops.utils.net;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Formatter;

import com.vmops.utils.NumbersUtil;

/**
 * This returns a class that can get the mac address.  Much of this code is
 * copied from the public domain utility from John Burkard.
 * @author <a href="mailto:jb@eaio.com">Johann Burkard</a>
 * @version 2.1.3
 **/
public class MacAddress {
    private long _addr = 0;

    protected MacAddress() {
    }

    public MacAddress(long addr) {
        _addr = addr;
    }

    public long toLong() {
        return _addr;
    }

    public byte[] toByteArray() {
        byte[] bytes = new byte[6];
        bytes[0] = (byte)((_addr >> 40) & 0xff);
        bytes[1] = (byte)((_addr >> 32) & 0xff);
        bytes[2] = (byte)((_addr >> 24) & 0xff);
        bytes[3] = (byte)((_addr >> 16) & 0xff);
        bytes[4] = (byte)((_addr >> 8) & 0xff);
        bytes[5] = (byte)((_addr >> 0) & 0xff);
        return bytes;
    }

    public String toString(String separator) {
        StringBuilder buff = new StringBuilder();
        Formatter formatter = new Formatter(buff);
        formatter.format("%02x%s%02x%s%02x%s%02x%s%02x%s%02x",
                         _addr >> 40 & 0xff, separator,
                         _addr >> 32 & 0xff, separator,
                         _addr >> 24 & 0xff, separator,
                         _addr >> 16 & 0xff, separator,
                         _addr >> 8 & 0xff, separator,
                         _addr & 0xff);
        return buff.toString();
        
        /*
        
        String str = Long.toHexString(_addr);

        for (int i = str.length() - 1; i >= 0; i--) {
            buff.append(str.charAt(i));
            if (separator != null && (str.length() - i) % 2 == 0) {
                buff.append(separator);
            }
        }
        return buff.reverse().toString();
        */
    }

    @Override
	public String toString() {
        return toString(":");
    }

    private static MacAddress s_address;
    static {
        String macAddress = null;

        Process p = null;
        BufferedReader in = null;

        try {
            String osname = System.getProperty("os.name");

            if (osname.startsWith("Windows")) {
                p = Runtime.getRuntime().exec(new String[] { "ipconfig", "/all"}, null);
            } else if (osname.startsWith("Solaris") || osname.startsWith("SunOS")) {
                // Solaris code must appear before the generic code
                String hostName = MacAddress.getFirstLineOfCommand(new String[] { "uname",
                                                                       "-n"});
                if (hostName != null) {
                    p = Runtime.getRuntime().exec(new String[] { "/usr/sbin/arp", hostName}, null);
                }
            } else if (new File("/usr/sbin/lanscan").exists()) {
                p = Runtime.getRuntime().exec(new String[] { "/usr/sbin/lanscan"}, null);
            } else if (new File("/sbin/ifconfig").exists()) {
                p = Runtime.getRuntime().exec(new String[] { "/sbin/ifconfig", "-a"}, null);
            }

            if (p != null) {
                in = new BufferedReader(new InputStreamReader(p.getInputStream()), 128);
                String l = null;
                while ((l = in.readLine()) != null) {
                    macAddress = MacAddress.parse(l);
                    if (macAddress != null && MacAddress.parseShort(macAddress) != 0xff)
                        break;
                }
            }

        } catch (SecurityException ex) {
        } catch (IOException ex) {
        } finally {
            if (p != null) {
                if (in != null) {
                    try {
                        in.close();
                    } catch (IOException ex) {
                    }
                }
                try {
                    p.getErrorStream().close();
                } catch (IOException ex) {
                }
                try {
                    p.getOutputStream().close();
                } catch (IOException ex) {
                }
                p.destroy();
            }
        }

        long clockSeqAndNode = 0;

        if (macAddress != null) {
            if (macAddress.indexOf(':') != -1) {
                clockSeqAndNode |= MacAddress.parseLong(macAddress);
            } else if (macAddress.startsWith("0x")) {
                clockSeqAndNode |= MacAddress.parseLong(macAddress.substring(2));
            }
        } else {
            try {
                byte[] local = InetAddress.getLocalHost().getAddress();
                clockSeqAndNode |= (local[0] << 24) & 0xFF000000L;
                clockSeqAndNode |= (local[1] << 16) & 0xFF0000;
                clockSeqAndNode |= (local[2] << 8) & 0xFF00;
                clockSeqAndNode |= local[3] & 0xFF;
            } catch (UnknownHostException ex) {
                clockSeqAndNode |= (long) (Math.random() * 0x7FFFFFFF);
            }
        }
    	
        s_address = new MacAddress(clockSeqAndNode);
    }

    public static MacAddress getMacAddress() {
        return s_address;
    }

    private static String getFirstLineOfCommand(String[] commands) throws IOException {

        Process p = null;
        BufferedReader reader = null;

        try {
            p = Runtime.getRuntime().exec(commands);
            reader = new BufferedReader(new InputStreamReader(p.getInputStream()), 128);

            return reader.readLine();
        } finally {
            if (p != null) {
                if (reader != null) {
                    try {
                        reader.close();
                    } catch (IOException ex) {
                    }
                }
                try {
                    p.getErrorStream().close();
                } catch (IOException ex) {
                }
                try {
                    p.getOutputStream().close();
                } catch (IOException ex) {
                }
                p.destroy();
            }
        }

    }

    /**
     * The MAC address parser attempts to find the following patterns:
     * <ul>
     * <li>.{1,2}:.{1,2}:.{1,2}:.{1,2}:.{1,2}:.{1,2}</li>
     * <li>.{1,2}-.{1,2}-.{1,2}-.{1,2}-.{1,2}-.{1,2}</li>
     * </ul>
     *
     * This is copied from the author below.  The author encouraged copying
     * it.
     * 
     */
    static String parse(String in) {

        // lanscan

        int hexStart = in.indexOf("0x");
        if (hexStart != -1) {
            int hexEnd = in.indexOf(' ', hexStart);
            if (hexEnd != -1) {
                return in.substring(hexStart, hexEnd);
            }
        }

        int octets = 0;
        int lastIndex, old, end;

        if (in.indexOf('-') > -1) {
            in = in.replace('-', ':');
        }

        lastIndex = in.lastIndexOf(':');

        if (lastIndex > in.length() - 2) return null;

        end = Math.min(in.length(), lastIndex + 3);

        ++octets;
        old = lastIndex;
        while (octets != 5 && lastIndex != -1 && lastIndex > 1) {
            lastIndex = in.lastIndexOf(':', --lastIndex);
            if (old - lastIndex == 3 || old - lastIndex == 2) {
                ++octets;
                old = lastIndex;
            }
        }

        if (octets == 5 && lastIndex > 1) {
            return in.substring(lastIndex - 2, end).trim();
        }
        return null;
    }

    public static void main(String[] args) {
        MacAddress addr = MacAddress.getMacAddress();
        System.out.println("addr in integer is " + addr.toLong());
        System.out.println("addr in bytes is " + NumbersUtil.bytesToString(addr.toByteArray(), 0, addr.toByteArray().length));
        System.out.println("addr in char is " + addr.toString(":"));
    }

private static final char[] DIGITS = { '0', '1', '2', '3', '4', '5', '6',
            '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f' };

    /**
     * Parses a <code>long</code> from a hex encoded number. This method will skip
     * all characters that are not 0-9 and a-f (the String is lower cased first).
     * Returns 0 if the String does not contain any interesting characters.
     * 
     * @param s the String to extract a <code>long</code> from, may not be <code>null</code>
     * @return a <code>long</code>
     * @throws NullPointerException if the String is <code>null</code>
     */
    public static long parseLong(String s) throws NullPointerException {
        s = s.toLowerCase();
        long out = 0;
        byte shifts = 0;
        char c;
        for (int i = 0; i < s.length() && shifts < 16; i++) {
            c = s.charAt(i);
            if ((c > 47) && (c < 58)) {
                out <<= 4;
                ++shifts;
                out |= c - 48;
            }
            else if ((c > 96) && (c < 103)) {
                ++shifts;
                out <<= 4;
                out |= c - 87;
            }
        }
        return out;
    }

    /**
     * Parses an <code>int</code> from a hex encoded number. This method will skip
     * all characters that are not 0-9 and a-f (the String is lower cased first).
     * Returns 0 if the String does not contain any interesting characters.
     * 
     * @param s the String to extract an <code>int</code> from, may not be <code>null</code>
     * @return an <code>int</code>
     * @throws NullPointerException if the String is <code>null</code>
     */
    public static int parseInt(String s) throws NullPointerException {
        s = s.toLowerCase();
        int out = 0;
        byte shifts = 0;
        char c;
        for (int i = 0; i < s.length() && shifts < 8; i++) {
            c = s.charAt(i);
            if ((c > 47) && (c < 58)) {
                out <<= 4;
                ++shifts;
                out |= c - 48;
            }
            else if ((c > 96) && (c < 103)) {
                ++shifts;
                out <<= 4;
                out |= c - 87;
            }
        }
        return out;
    }

    /**
     * Parses a <code>short</code> from a hex encoded number. This method will skip
     * all characters that are not 0-9 and a-f (the String is lower cased first).
     * Returns 0 if the String does not contain any interesting characters.
     * 
     * @param s the String to extract a <code>short</code> from, may not be <code>null</code>
     * @return a <code>short</code>
     * @throws NullPointerException if the String is <code>null</code>
     */
    public static short parseShort(String s) throws NullPointerException {
        s = s.toLowerCase();
        short out = 0;
        byte shifts = 0;
        char c;
        for (int i = 0; i < s.length() && shifts < 4; i++) {
            c = s.charAt(i);
            if ((c > 47) && (c < 58)) {
                out <<= 4;
                ++shifts;
                out |= c - 48;
            }
            else if ((c > 96) && (c < 103)) {
                ++shifts;
                out <<= 4;
                out |= c - 87;
            }
        }
        return out;
    }

    /**
     * Parses a <code>byte</code> from a hex encoded number. This method will skip
     * all characters that are not 0-9 and a-f (the String is lower cased first).
     * Returns 0 if the String does not contain any interesting characters.
     * 
     * @param s the String to extract a <code>byte</code> from, may not be <code>null</code>
     * @return a <code>byte</code>
     * @throws NullPointerException if the String is <code>null</code>
     */
    public static byte parseByte(String s) throws NullPointerException {
        s = s.toLowerCase();
        byte out = 0;
        byte shifts = 0;
        char c;
        for (int i = 0; i < s.length() && shifts < 2; i++) {
            c = s.charAt(i);
            if ((c > 47) && (c < 58)) {
                out <<= 4;
                ++shifts;
                out |= c - 48;
            }
            else if ((c > 96) && (c < 103)) {
                ++shifts;
                out <<= 4;
                out |= c - 87;
            }
        }
        return out;
    }
}
