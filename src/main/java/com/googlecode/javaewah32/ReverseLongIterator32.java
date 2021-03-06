package com.googlecode.javaewah32;

/*
 * Copyright 2009-2014, Daniel Lemire, Cliff Moon, David McIntosh, Robert Becho, Google Inc., Veronika Zenz, Owen Kaser, Gregory Ssi-Yan-Kai, Rory Graves
 * Licensed under the Apache License, Version 2.0.
 */

import com.googlecode.javaewah.LongIterator;

import static com.googlecode.javaewah32.EWAHCompressedBitmap32.WORD_IN_BITS;

/**
 * The ReverseLongIterator32 is the 32 bit implementation of the LongIterator
 * interface, which efficiently returns the stream of longs represented by a
 * ReverseEWAHIterator32 in reverse order.
 *
 * @author Gregory Ssi-Yan-Kai
 */
final class ReverseLongIterator32 implements LongIterator {

    private final ReverseEWAHIterator32 ewahIter;
    private final long sizeInBits;
    private final Buffer buffer;
    private long position;
    private boolean runningBit;
    private long runningLength;
    private int word;
    private int wordPosition;
    private int wordLength;
    private long literalPosition;
    private boolean hasNext;

    ReverseLongIterator32(ReverseEWAHIterator32 ewahIter, long sizeInBits) {
        this.ewahIter = ewahIter;
        this.sizeInBits = sizeInBits;
        this.buffer = ewahIter.buffer();
        this.position = sizeInBits;
        this.runningLength = Integer.MAX_VALUE;
        this.hasNext = this.moveToPreviousRLW();
    }

    @Override
    public boolean hasNext() {
        return this.hasNext;
    }

    @Override
    public long next() {
        final long answer;
        if (runningHasNext()) {
            answer = this.position--;
        } else {
            final int t = this.word & -this.word;
            answer = this.literalPosition - Integer.bitCount(t - 1);
            this.word ^= t;
        }
        this.hasNext = this.moveToPreviousRLW();
        return answer;
    }

    private boolean moveToPreviousRLW() {
        while (!literalHasNext() && !runningHasNext()) {
            if (!this.ewahIter.hasPrevious()) {
                return false;
            }
            setRLW(this.ewahIter.previous());
        }
        return true;
    }

    private void setRLW(RunningLengthWord32 rlw) {
        this.wordLength = rlw.getNumberOfLiteralWords();
        this.wordPosition = this.ewahIter.position();
        this.runningLength = this.position - WORD_IN_BITS * (rlw.getRunningLength() + this.wordLength);
        this.runningBit = rlw.getRunningBit();
        this.position--;
    }

    private boolean runningHasNext() {
        return this.runningBit && this.runningLength <= this.position;
    }

    private boolean literalHasNext() {
        while (this.word == 0 && this.wordLength > 0) {
            this.word = Integer.reverse(this.buffer.getWord(this.wordPosition + this.wordLength--));
            if (this.position == this.sizeInBits - 1) {
                final int usedBitsInLast = (int) (this.sizeInBits % WORD_IN_BITS);
                if (usedBitsInLast > 0) {
                    this.word = (this.word >>> (WORD_IN_BITS - usedBitsInLast));
                }
            }
            this.literalPosition = this.position;
            this.position -= WORD_IN_BITS;
        }
        return this.word != 0;
    }

}
