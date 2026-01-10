package io.benwiegand.attitude.adb;

import android.os.SystemClock;
import android.util.Base64;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeoutException;

import io.github.muntashirakon.adb.AdbStream;

/**
 * quick & dirty adb shell.
 * there are better ways to do this, but this works without needing any special executables or fancy IPC servers.
 */
public class QdAdbShell {
    private static final String TAG = QdAdbShell.class.getSimpleName();
    private static final boolean DEBUG_LOGS = false;

    private static final long INIT_TIMEOUT = 5000;

    // timeout between expected consecutive reads
    // these should ideally happen immediately (with no delay) but must account for system load
    private static final long INTERNAL_TIMEOUT = 1000;

    private static final String QD_INIT_MARKER = "!!!qd!!!";
    private static final String QD_RESULT_SEPARATOR = "|";
    private static final String QD_RESULT_START_END_MARKER = "|||";
    private static final String QD_COMMAND_ENTRY_MARKER = ">>>";

    // not the safest way of serializing command outputs, but it should work for the predictable inputs I'll be passing
    private static final String QD_SHELL_SCRIPT = """
            qdshell() {
                set -o pipefail
                printf '!!!'
                printf 'qd'
                printf '!!!'
                while true; do
                    printf '>>>'
                    read -r cmd
                    out="`(eval "$cmd") 2>&1 | base64 -w0`"
                    ret="$?"
                    len="`printf '%s' "$out" | wc -c`"
                    printf '|||%s|%s|%s|||' "$ret" "$len" "$out"
                done
            }
            qdshell
            """;

    public record Result(int returnCode, byte[] out) {}
    public static class ExecutionException extends Exception {
        private final boolean sentOff;
        private ExecutionException(String message, Throwable cause, boolean sentOff) {
            super(message, cause);
            this.sentOff = sentOff;
        }

        /**
         * if true, there's a good chance that the command was executed
         * @return true if command might have been executed, false otherwise
         */
        public boolean isCmdSentOff() {
            return sentOff;
        }
    }


    private final AdbStream shellStream;
    private final InputStreamReader inputStreamReader;
    private Throwable error = null;
    private boolean init = false;
    private boolean dead = false;

    private final char[] buffer = new char[1024];
    private int bufferIndex = 0;


    public QdAdbShell(AdbStream shellStream) {
        this.shellStream = shellStream;
        InputStream inputStream = shellStream.openInputStream();
        inputStreamReader = new InputStreamReader(inputStream, StandardCharsets.UTF_8);
    }

    private void closeWithError(Throwable t) {
        if (dead) {
            Log.wtf(TAG, "closeWithError() called after shell already died");
            return;
        }

        Log.e(TAG, "qd shell died", t);
        error = t;
        dead = true;
        try {
            if (!shellStream.isClosed()) shellStream.close();
        } catch (Throwable t2) {
            Log.d(TAG, "got exception while closing stream due to error", t2);
        }

        try {
            inputStreamReader.close();
        } catch (Throwable t2) {
            Log.d(TAG, "got exception while closing InputStreamReader", t2);
        }
    }

    private void assertNext(String marker, long timeout) throws IOException, TimeoutException {
        if (DEBUG_LOGS) Log.d(TAG, "asserting marker: " + marker);

        long deadline = SystemClock.elapsedRealtime() + timeout;
        int markerIndex = 0;
        int len;
        bufferIndex = 0;

        while (markerIndex < marker.length() && SystemClock.elapsedRealtime() < deadline) {

            if (inputStreamReader.ready()) {
                len = inputStreamReader.read(buffer, 0, 1);
                if (len < 0) {
                    Log.e(TAG, "end of stream (" + len + ")");
                    throw new IOException("end of stream");
                } else if (len == 0) {
                    continue;
                }

                if (buffer[0] == marker.charAt(markerIndex)) {
                    markerIndex++;
                } else {
                    char expectation = marker.charAt(markerIndex);
                    char reality = buffer[0];
                    throw new AssertionError("expected next char to be '" + expectation + "' (" + ((int) expectation) + ") but got '" + buffer[0] + "' (" + ((int) reality) + "). index = " + markerIndex);
                }
            }

        }

        if (markerIndex < marker.length()) {
            Log.e(TAG, "timed out before reaching marker");
            throw new TimeoutException("timed out");
        }
    }

    private String readUntil(String marker, long timeout) throws IOException, TimeoutException {
        if (DEBUG_LOGS) Log.d(TAG, "reading until: " + marker);

        StringBuilder sb = new StringBuilder();
        long deadline = SystemClock.elapsedRealtime() + timeout;
        int markerIndex = 0;
        int len;
        bufferIndex = 0;

        while (markerIndex < marker.length() && SystemClock.elapsedRealtime() < deadline) {

            if (inputStreamReader.ready()) {
                len = inputStreamReader.read(buffer, bufferIndex, 1);
                if (len < 0) {
                    Log.e(TAG, "end of stream (" + len + ")");
                    throw new IOException("end of stream");
                } else if (len == 0) {
                    continue;
                }

                bufferIndex += len;
                if (buffer[bufferIndex - 1] == marker.charAt(markerIndex)) {
                    markerIndex++;
                } else {
                    markerIndex = 0;
                }
            }

            if (bufferIndex == buffer.length) {
                sb.append(buffer, 0, bufferIndex);
                bufferIndex = 0;
            }

        }

        if (markerIndex < marker.length()) {
            Log.e(TAG, "timed out before reaching marker");
            throw new TimeoutException("timed out");
        }

        sb.append(buffer, 0, bufferIndex);
        bufferIndex = 0;

        return sb.toString();
    }

    private String readString(int totalLength) throws IOException, TimeoutException {

        StringBuilder sb = new StringBuilder(totalLength);
        long deadline = SystemClock.elapsedRealtime() + INTERNAL_TIMEOUT;
        int len;
        int remaining = totalLength;
        bufferIndex = 0;

        while (remaining > 0 && SystemClock.elapsedRealtime() < deadline) {
            if (!inputStreamReader.ready()) continue;

            len = inputStreamReader.read(buffer, bufferIndex, Math.min(buffer.length - bufferIndex, remaining));
            if (len < 0) {
                Log.e(TAG, "end of stream (" + len + ")");
                throw new IOException("end of stream");
            } else if (len == 0) {
                continue;
            }

            bufferIndex += len;
            remaining -= len;
            assert remaining > -1;

            if (bufferIndex >= buffer.length / 2) {
                sb.append(buffer, 0, bufferIndex);
                bufferIndex = 0;
            }

        }

        if (remaining > 0) {
            Log.e(TAG, "timed out before reading entire string (" + remaining + " of " + totalLength + " bytes left)");
            throw new TimeoutException("timed out");
        }

        sb.append(buffer, 0, bufferIndex);
        bufferIndex = 0;
        return sb.toString();
    }

    private int readInt() throws IOException, TimeoutException {
        String s = readUntil(QdAdbShell.QD_RESULT_SEPARATOR, QdAdbShell.INTERNAL_TIMEOUT);

        // result contains marker
        assert s.endsWith(QdAdbShell.QD_RESULT_SEPARATOR);
        s = s.substring(0, s.length() - QdAdbShell.QD_RESULT_SEPARATOR.length());

        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            throw new AssertionError("expected an integer", e);
        }
    }


    /**
     * initialize the shell so it can be used.
     * @return true if successful and ready to use, false otherwise. if false, the initialization error can be retrieved with getError().
     */
    public boolean init() {
        if (init) throw new IllegalStateException("init() should only be called once");
        init = true;

        Log.i(TAG, "init");
        try {
            byte[] scriptBytes = QD_SHELL_SCRIPT.getBytes();
            shellStream.write(scriptBytes, 0, scriptBytes.length);

            readUntil(QD_INIT_MARKER, INIT_TIMEOUT);
            return true;
        } catch (Throwable t) {
            closeWithError(t);
            // don't throw otherwise caller will need a giant try/catch for this single method call.
            // and the error doesn't really matter much anyway, all of them can indicate both temporary and permanent problems.
            return false;
        }
    }

    /**
     * executes a shell command. (dangerously)
     * <ul>
     *     <li>this should ONLY be used to execute correctly formatted commands which have KNOWN/SAFE INPUTS.</li>
     *     <li>it should go without saying to NEVER PASS USER INPUT INTO THIS!!!</li>
     *     <li>don't use this for long running commands.</li>
     *     <li>this is not threadsafe. use AdbService if you need a threadsafe wrapper around this.</li>
     * </ul>
     * <p>
     *     if the executed command returns an error, it will be returned as part of the result.<br>
     *     if command execution fails for some reason, (timeout or otherwise,) an ExecutionException is returned (see "throws" below for specifics)
     * </p>
     * @param cmd the command to execute (with a predictable known/safe format)
     * @param cmdTimeout the timeout for command execution in milliseconds. this does not apply to processing of the result.
     * @return a Result record containing the return code and raw byte output of the command
     * @throws ExecutionException if something went wrong. if ExecutionException.isCmdSentOff() is true, the command may have still been executed.
     * the real error (TimeoutException, IOException, or subclass of RuntimeException) is returned as the cause and can be retrieved through QdAdbShell.getError().
     */
    public Result execute(String cmd, long cmdTimeout) throws ExecutionException {
        if (!init) throw new IllegalStateException("must first call init()");
        if (dead) throw new IllegalStateException("shell is dead");

        // newline characters in the command itself will break things
        assert !cmd.contains("\n");
        assert !cmd.contains("\r");

        Log.v(TAG, "running command: " + cmd);
        boolean sentOff = false;
        try {
            assertNext(QD_COMMAND_ENTRY_MARKER, INTERNAL_TIMEOUT);
            // using \r\n here causes double input...
            byte[] commandBytes = (cmd + "\n").getBytes(StandardCharsets.UTF_8);
            shellStream.write(commandBytes, 0, commandBytes.length);
            sentOff = true;
            shellStream.flush();

            // ...but it echoes with \r\n
            assertNext(cmd + "\r\n", INTERNAL_TIMEOUT);

            // result start marker happens once command exits, use the real timeout here
            assertNext(QD_RESULT_START_END_MARKER, cmdTimeout);

            int returnCode = readInt();
            Log.d(TAG,  "got return code: " + returnCode);

            int outLen = readInt();
            Log.d(TAG,  "got out length: " + outLen);

            // output is wrapped in base64 to avoid issues with lf vs crlf
            // the length allows it to buffer a large amount of the output at one time
            String out64 = readString(outLen);

            if (DEBUG_LOGS) Log.d(TAG, "got out (encoded): " + out64);
            assertNext(QD_RESULT_START_END_MARKER, INTERNAL_TIMEOUT);

            byte[] out = Base64.decode(out64, Base64.DEFAULT);
            if (DEBUG_LOGS) Log.d(TAG, "length of out (decoded): " + out.length);

            Log.d(TAG, "command execution finished cleanly");
            return new Result(returnCode, out);

        } catch (AssertionError e) {
            // likely caused by a malformed command, which would be a bug in the caller.
            // qd shell is only supposed to be used with known good inputs, otherwise things like this can certainly happen.
            Log.wtf(TAG, "qd shell closing due to failed assertion. this could be a bug!", e);
            closeWithError(e);
        } catch (TimeoutException e) {
            // the shell must also die and be recreated after this, since there's no way to safely abort
            closeWithError(e);
        } catch (Throwable t) {
            // generic error (likely network problem or invalid chars)
            closeWithError(t);
        }

        // error happened
        // if it happened after the command was fully sent off, there's a high chance that it already executed
        // the caller should be informed of this so it doesn't perform the same operation twice
        throw new ExecutionException(
                "a problem happened while executing the command. " + (sentOff ? "the command may have been executed" : "the command likely didn't get executed"),
                error, sentOff);
    }

    public boolean isDead() {
        return dead;
    }

    public Throwable getError() {
        return error;
    }

    public void close() {
        if (dead) {
            Log.w(TAG, "shell already closed");
            return;
        }
        Log.v(TAG, "closing shell");

        try {
            if (!shellStream.isClosed()) shellStream.close();
        } catch (Throwable t) {
            Log.d(TAG, "got exception while closing stream due to error", t);
            error = t;
        } finally {
            dead = true;
        }

        try {
            inputStreamReader.close();
        } catch (Throwable t) {
            Log.d(TAG, "got exception while closing InputStreamReader", t);
        }
    }

}
