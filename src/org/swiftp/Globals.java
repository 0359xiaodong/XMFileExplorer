/*
Copyright 2009 David Revell

This file is part of SwiFTP.

SwiFTP is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

SwiFTP is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with SwiFTP.  If not, see <http://www.gnu.org/licenses/>.
*/

package org.swiftp;

import java.io.File;

import android.content.Context;

/**
 * 职责：存放
 * 1.Context context
 * 2.String lastError 代表错误的TAG，在Log中为Error或Warn的
 * 3.File chrootDir
 * 4.ProxyConnector proxyConnetor
 * 5.String username
 * */
public class Globals {
    private static Context context;
    /**
     * 存放错误TAG，在Log中为Error或Warn的
     * */
    private static String lastError;
    private static File chrootDir = new File(Defaults.chrootDir);
    private static ProxyConnector proxyConnector = null;
    private static String username = null;

    public static ProxyConnector getProxyConnector() {
        if(proxyConnector != null) {
            if(!proxyConnector.isAlive()) {
                return null;
            }
        }
        return proxyConnector;
    }

    public static void setProxyConnector(ProxyConnector proxyConnector) {
        Globals.proxyConnector = proxyConnector;
    }

    public static File getChrootDir() {
        return chrootDir;
    }

    public static void setChrootDir(File chrootDir) {
        if(chrootDir.isDirectory()) {
            Globals.chrootDir = chrootDir;
        }
    }

    public static String getLastError() {
        return lastError;
    }

    public static void setLastError(String lastError) {
        Globals.lastError = lastError;
    }

    public static Context getContext() {
        return context;
    }

    public static void setContext(Context context) {
        if(context != null) {
            Globals.context = context;
        }
    }

    public static String getUsername() {
        return username;
    }

    public static void setUsername(String username) {
        Globals.username = username;
    }

}
