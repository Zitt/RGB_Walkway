package com.pinball_mods.walkwayrgb;

import java.util.concurrent.atomic.AtomicInteger;

public class IsDayTime extends AtomicInteger {
    public enum Values {
        unknown, isNightTime, isDayTime;
        public static final Values values[] = values();
    }

    /*public IsDayTime(Values Val) {
        this.set((int)(Val));
    }*/
    public IsDayTime( Values val ) {
       this.set( val.ordinal() );
    }

    public IsDayTime( String str ) {
        this.set( Values.valueOf(str).ordinal() );
    }

    public void setEnum( Values val ) {
        this.set(val.ordinal());
    }

    public Values getEnum() {
        //return Values.values()[this.get()];
        return Values.values[this.get()];
    }
}
