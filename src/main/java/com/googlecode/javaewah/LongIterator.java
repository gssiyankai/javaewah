package com.googlecode.javaewah;

/*
 * Copyright 2009-2014, Daniel Lemire, Cliff Moon, David McIntosh, Robert Becho, Google Inc., Veronika Zenz, Owen Kaser, Gregory Ssi-Yan-Kai, Rory Graves
 * Licensed under the Apache License, Version 2.0.
 */

/**
 * The LongIterator interface is used to iterate over a stream of longs.
 *
 * @author Gregory Ssi-Yan-Kai
 */
public interface LongIterator {

    /**
     * Is there more?
     *
     * @return true, if there is more, false otherwise
     */
    boolean hasNext();

    /**
     * Return the next long
     *
     * @return the long
     */
    long next();
}
