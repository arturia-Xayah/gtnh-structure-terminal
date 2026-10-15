package dev.structureterminal.core;

/** Zero progress stops a job; missing materials must never spin forever. */
public final class BuildProgress {

    public enum State {
        RUNNING,
        COMPLETE,
        BLOCKED,
        UNSUPPORTED,
        LIMIT
    }

    private int rounds;
    private long placed;
    private State state = State.RUNNING;

    public State accept(int result, int maximumRounds) {
        if (state != State.RUNNING) throw new IllegalStateException("Job already stopped");
        rounds++;
        if (result == -1) state = State.COMPLETE;
        else if (result == -2) state = State.UNSUPPORTED;
        else if (result <= 0) state = State.BLOCKED;
        else {
            placed += result;
            if (rounds >= maximumRounds) state = State.LIMIT;
        }
        return state;
    }

    public long placed() {
        return placed;
    }

    public int rounds() {
        return rounds;
    }

    public State state() {
        return state;
    }
}
