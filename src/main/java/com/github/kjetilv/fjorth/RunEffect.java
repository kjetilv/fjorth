package com.github.kjetilv.fjorth;

import module java.base;

class RunEffect implements Word.Effect {

    private final Runnable effect;

    RunEffect(Runnable effect) {
        this.effect = effect;
    }

    @Override
    public void apply(InterpreterImpl interpreter) {
        effect.run();
    }
}
