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
package com.vmops.agent.api;

public class Answer extends Command {
    boolean result;
    String details;
    
    protected Answer() {
    }
    
    public Answer(Command command) {
        this(command, true, null);
    }
    
    public Answer(Command command, boolean success, String details) {
        result = success;
        this.details = details;
    }
    
    public boolean getResult() {
        return result;
    }
    
    public String getDetails() {
        return details;
    }
    
    @Override
    public boolean executeInSequence() {
        return false;
    }
    
    public static Answer createUnsupportedCommandAnswer(Command cmd) {
        return new Answer(cmd, false, "Unsupported command issued:" + cmd.toString() + ".  Are you sure you got the right type of server?");
    }
    
    public static Answer createUnsupportedVersionAnswer(Command cmd) {
        return new Answer(cmd, false, "Unsuppored Version.");
    }
}
