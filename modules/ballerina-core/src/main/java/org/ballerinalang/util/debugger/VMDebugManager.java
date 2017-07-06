/*
*  Copyright (c) 2017, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
*
*  WSO2 Inc. licenses this file to you under the Apache License,
*  Version 2.0 (the "License"); you may not use this file except
*  in compliance with the License.
*  You may obtain a copy of the License at
*
*    http://www.apache.org/licenses/LICENSE-2.0
*
*  Unless required by applicable law or agreed to in writing,
*  software distributed under the License is distributed on an
*  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
*  KIND, either express or implied.  See the License for the
*  specific language governing permissions and limitations
*  under the License.
*/

package org.ballerinalang.util.debugger;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.channel.Channel;
import org.ballerinalang.bre.Context;
import org.ballerinalang.bre.bvm.DebuggerExecutor;
import org.ballerinalang.bre.nonblocking.debugger.BreakPointInfo;
import org.ballerinalang.runtime.threadpool.ThreadPoolFactory;
import org.ballerinalang.util.codegen.ProgramFile;
import org.ballerinalang.util.debugger.dto.CommandDTO;
import org.ballerinalang.util.debugger.dto.MessageDTO;
import org.ballerinalang.util.exceptions.BallerinaException;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Semaphore;


/**
 * {@code VMDebugManager} Manages debug sessions and handle debug related actions.
 *
 * @since 0.88
 */
public class VMDebugManager {
    /**
     * The Execution sem. used to block debugger till client connects.
     */
    private volatile Semaphore executionWaitSem;

    private VMDebugServer debugServer;

    private boolean debugEnabled;

    /**
     * Object to hold debug session related context.
     */
    private VMDebugSession debugSession;

    private static VMDebugManager debugManagerInstance = null;

    private static boolean debugManagerInitialized = false;

    /**
     * Instantiates a new Debug manager.
     */
    private VMDebugManager() {
        executionWaitSem = new Semaphore(0);
        debugServer = new VMDebugServer();
        debugSession = new VMDebugSession();
    }

    /**
     * Debug manager singleton.
     *
     * @return DebugManager instance
     */
    public static VMDebugManager getInstance() {
        synchronized (VMDebugManager.class) {
            if (debugManagerInstance == null) {
                debugManagerInstance = new VMDebugManager();
            }
        }
        return debugManagerInstance;
    }

    /**
     * Initializes the debug manager single instance.
     */
    public synchronized void serviceInit() {
        if (this.debugManagerInitialized) {
            throw new BallerinaException("Debugger instance already initialized");
        }
        // start the debug server if it is not started yet.
        this.debugServer.startServer();
        this.debugManagerInitialized = true;
    }

    public synchronized void mainInit(ProgramFile programFile, Context mainThreadContext) {
        if (this.debugManagerInitialized) {
            throw new BallerinaException("Debugger instance already initialized");
        }
        mainThreadContext.setDebugInfoHolder(new DebugInfoHolder());
        mainThreadContext.setDebugEnabled(true);
        DebuggerExecutor debuggerExecutor = new DebuggerExecutor(programFile, mainThreadContext);
        ExecutorService executor = ThreadPoolFactory.getInstance().getWorkerExecutor();
        mainThreadContext.getDebugInfoHolder().setDebugSessionObserver(debugSession);
        executor.submit(debuggerExecutor);
        // start the debug server if it is not started yet.
        this.debugServer.startServer();
    }

    /**
     * Process debug command.
     *
     * @param json the json
     */
    public void processDebugCommand(String json) {
        ObjectMapper mapper = new ObjectMapper();
        CommandDTO command = null;
        try {
            command = mapper.readValue(json, CommandDTO.class);
        } catch (IOException e) {
            // Set the command to invalid so an invalid message will be passed from -
            // default block
            command.setCommand("invalid");
        }
        DebugInfoHolder holder = null;
        if (debugSession.getContext("main") != null) {
            holder = debugSession.getContext("main").getDebugInfoHolder(); //todo fix
        }
        switch (command.getCommand()) {
            case DebugConstants.CMD_RESUME:
                holder.resume();
                break;
            case DebugConstants.CMD_STEP_OVER:
                holder.stepOver();
                break;
            case DebugConstants.CMD_STEP_IN:
                holder.stepIn();
                break;
            case DebugConstants.CMD_STEP_OUT:
                holder.stepOut();
                break;
            case DebugConstants.CMD_STOP:
                holder.clearDebugLocations();
                debugSession.clearSession();
                holder.resume();
                break;
            case DebugConstants.CMD_SET_POINTS:
                // we expect { "command": "SET_POINTS", points: [{ "fileName": "sample.bal", "lineNumber" : 5 },{...}]}
                debugSession.addDebugPoints(command.getBreakPoints());
                sendAcknowledge(this.debugSession, "Debug points updated");
                break;
            case DebugConstants.CMD_START:
                // Client needs to explicitly start the execution once connected.
                // This will allow client to set the breakpoints before starting the execution.
                sendAcknowledge(this.debugSession, "Debug started.");
                holder.resume();
                break;
            default:
                MessageDTO message = new MessageDTO();
                message.setCode(DebugConstants.CODE_INVALID);
                message.setMessage(DebugConstants.MSG_INVALID);
                debugServer.pushMessageToClient(debugSession, message);
        }
    }

    /**
     * Set debug channel.
     *
     * @param channel the channel
     */
    public void addDebugSession(Channel channel) throws DebugException {
        this.debugSession.setChannel(channel);
        sendAcknowledge(this.debugSession, "Channel registered.");
    }

    /**
     *  Hold on to main thread while debugger finishes execution.
     */
    public void holdON() {
        //suspend the current thread till debugging process finishes
        try {
            executionWaitSem.acquire();
            debugSession.notifyExit();
        } catch (InterruptedException e) {
            // Do nothing probably someone wants to shutdown the thread.
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Add {@link Context} to current execution.
     *
     * @param bContext context to run
     */
    public void setDebuggerContext(Context bContext) {
        // if we are handling multiple connections
        // we need to check and set to correct debugger session
        if (!isDebugSessionActive()) {
            throw new IllegalStateException("Debug session has not initialize, Unable to set debugger.");
        }
        bContext.getDebugInfoHolder().setDebugSessionObserver(debugSession);
        this.debugSession.addContext(bContext);
    }

    public boolean isDebugEnabled() {
        return debugEnabled;
    }

    public void setDebugEnabled(boolean debugEnabled) {
        this.debugEnabled = debugEnabled;
    }

    public void releaseExecutionLock() {
        this.executionWaitSem.release();
    }

    public boolean isDebugSessionActive() {
        return (this.debugSession.getChannel() != null);
    }


    /**
     * Send a message to the debug client when a breakpoint is hit.
     *
     * @param debugSession current debugging session
     * @param breakPointInfo info of the current break point
     */
    public void notifyDebugHit(VMDebugSession debugSession, BreakPointInfo breakPointInfo) {
        MessageDTO message = new MessageDTO();
        message.setCode(DebugConstants.CODE_HIT);
        message.setMessage(DebugConstants.MSG_HIT);
        message.setLocation(breakPointInfo.getHaltLocation());
        message.setFrames(breakPointInfo.getCurrentFrames());
        debugServer.pushMessageToClient(debugSession, message);
    }


    /**
     * Notify client when debugger has finish execution.
     *
     * @param debugSession current debugging session
     */
    public void notifyComplete(VMDebugSession debugSession) {
        MessageDTO message = new MessageDTO();
        message.setCode(DebugConstants.CODE_COMPLETE);
        message.setMessage(DebugConstants.MSG_COMPLETE);
        debugServer.pushMessageToClient(debugSession, message);
    }

    /**
     * Notify client when the debugger is exiting.
     *
     * @param debugSession current debugging session
     */
    public void notifyExit(VMDebugSession debugSession) {
        MessageDTO message = new MessageDTO();
        message.setCode(DebugConstants.CODE_EXIT);
        message.setMessage(DebugConstants.MSG_EXIT);
        debugServer.pushMessageToClient(debugSession, message);
    }

    /**
     * Send a generic acknowledge message to the client.
     *
     * @param debugSession current debugging session
     * @param messageText message to send to the client
     */
    public void sendAcknowledge(VMDebugSession debugSession, String messageText) {
        MessageDTO message = new MessageDTO();
        message.setCode(DebugConstants.CODE_ACK);
        message.setMessage(messageText);
        debugServer.pushMessageToClient(debugSession, message);
    }
}
