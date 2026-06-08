package com.game.character;

import java.util.ArrayList;
import java.util.List;

public class CharacterStateMachine {

    private CharacterState currentState = CharacterState.IDLE;
    private final List<StateListener> listeners = new ArrayList<>(2);

    @FunctionalInterface
    public interface StateListener {
        void onStateChanged(CharacterState oldState, CharacterState newState);
    }

    public void changeState(CharacterState newState) {
        if (newState == currentState) return;

        CharacterState oldState = currentState;
        currentState = newState;

        for (StateListener listener : listeners) {
            listener.onStateChanged(oldState, newState);
        }
    }

    public CharacterState getCurrentState() {
        return currentState;
    }

    public void addListener(StateListener listener) {
        listeners.add(listener);
    }

    public void removeListener(StateListener listener) {
        listeners.remove(listener);
    }
}
