package com.hq.android.hserver.sdk.nano;

public interface IHandler<I, O> {

    O handle(I input);
}
