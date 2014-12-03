package com.googlecode.javaewah32;

/*
 * Copyright 2009-2014, Daniel Lemire, Cliff Moon, David McIntosh, Robert Becho, Google Inc., Veronika Zenz, Owen Kaser, Gregory Ssi-Yan-Kai, Rory Graves
 * Licensed under the Apache License, Version 2.0.
 */

import com.googlecode.javaewah.ChunkIterator;

import static com.googlecode.javaewah32.EWAHCompressedBitmap32.WORD_IN_BITS;

/**
 * The ChunkIteratorImpl is the 32 bit implementation of the ChunkIterator
 * interface, which efficiently returns the chunks of ones and zeros represented by an
 * EWAHIterator.
 *
 * @author Gregory Ssi-Yan-Kai
 */
final class ChunkIteratorImpl32 implements ChunkIterator {

    private final EWAHIterator32 ewahIter;
    private final long sizeInBits;
    private final Buffer buffer;
    private long position;
    private boolean runningBit;
    private long runningLength;
    private int word;
    private int wordMask;
    private int wordPosition;
    private int wordLength;
    private boolean hasNext;
    private Boolean nextBit;
    private long nextLength;

    ChunkIteratorImpl32(EWAHIterator32 ewahIter, long sizeInBits) {
        this.ewahIter = ewahIter;
        this.sizeInBits = sizeInBits;
        this.buffer = ewahIter.buffer();
        this.hasNext = moveToNextRLW();
    }

    @Override
    public boolean hasNext() {
        return this.hasNext;
    }

    @Override
    public boolean nextBit() {
        return this.nextBit;
    }

    @Override
    public long nextLength() {
        return this.nextLength;
    }

    @Override
    public void move() {
        move(this.nextLength);
    }

    @Override
    public void move(long bits) {
        this.nextLength -= bits;
        if(this.nextLength <= 0) {
            do {
                this.nextBit = null;
                updateNext();
                this.hasNext = moveToNextRLW();
            } while(this.nextLength < 0);
        }
    }

    private boolean moveToNextRLW() {
        while (!runningHasNext() && !literalHasNext()) {
            if (!hasNextRLW()) {
                return this.nextBit!=null;
            }
            setRLW(nextRLW());
            updateNext();
        }
        return true;
    }

    private void setRLW(RunningLengthWord32 rlw) {
        this.runningLength = Math.min(this.sizeInBits,
                                      this.position + WORD_IN_BITS * rlw.getRunningLength());
        this.runningBit = rlw.getRunningBit();
        this.wordPosition = this.ewahIter.literalWords();
        this.wordLength = this.wordPosition + rlw.getNumberOfLiteralWords();
    }

    private boolean runningHasNext() {
        return this.position < this.runningLength;
    }

    private boolean literalHasNext() {
        while (this.word == 0 && this.wordPosition < this.wordLength) {
            this.word = this.buffer.getWord(this.wordPosition++);
            this.wordMask = 1;
        }
        return this.word != 0 || (!hasNextRLW() && this.position < this.sizeInBits);
    }

    private boolean hasNextRLW() {
        return this.ewahIter.hasNext();
    }

    private RunningLengthWord32 nextRLW() {
        return this.ewahIter.next();
    }

    private void updateNext() {
        if(runningHasNext()) {
            if(this.nextBit == null || this.nextBit == this.runningBit) {
                this.nextBit = this.runningBit;
                long offset = runningOffset();
                this.nextLength += offset;
                movePosition(offset);
                updateNext();
            }
        } else if (literalHasNext()) {
            boolean b = currentWordBit();
            if(this.nextBit == null || this.nextBit == b) {
                this.nextBit = b;
                this.nextLength++;
                movePosition(1);
                shiftWordMask();
                updateNext();
            }
        } else {
            moveToNextRLW();
        }
    }

    private long runningOffset() {
        return this.runningLength - this.position;
    }

    private void movePosition(long offset) {
        this.position += offset;
    }

    private boolean currentWordBit() {
        return (this.word & this.wordMask) != 0;
    }

    private void shiftWordMask() {
        this.word &= ~this.wordMask;
        this.wordMask = this.wordMask << 1;
    }

}
