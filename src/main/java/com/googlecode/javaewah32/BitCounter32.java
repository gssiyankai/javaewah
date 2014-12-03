package com.googlecode.javaewah32;

/*
 * Copyright 2009-2014, Daniel Lemire, Cliff Moon, David McIntosh, Robert Becho, Google Inc., Veronika Zenz, Owen Kaser, Gregory Ssi-Yan-Kai, Rory Graves
 * Licensed under the Apache License, Version 2.0.
 */

/**
 * BitCounter is a fake bitset data structure. Instead of storing the actual
 * data, it only records the number of set bits.
 *
 * @author Daniel Lemire and David McIntosh
 * @since 0.5.0
 */

public final class BitCounter32 implements BitmapStorage32 {

    /**
     * Virtually add words directly to the bitmap
     *
     * @param newData the word
     */
    @Override
    public void addWord(final int newData) {
        this.oneBits += Integer.bitCount(newData);
    }
    
    /**
     * Virtually add literal words directly to the bitmap
     *
     * @param newData the word
     */
    @Override
    public void addLiteralWord(final int newData) {
        this.oneBits += Integer.bitCount(newData);
    }

    /**
     * virtually add several literal words.
     *
     * @param data   the literal words
     * @param start  the starting point in the array
     * @param number the number of literal words to add
     */
    @Override
    public void addStreamOfLiteralWords(int[] data, int start, int number) {
        for (int i = start; i < start + number; i++) {
            addLiteralWord(data[i]);
        }
    }

    /**
     * virtually add many zeroes or ones.
     *
     * @param v      zeros or ones
     * @param number how many to words add
     */
    @Override
    public void addStreamOfEmptyWords(boolean v, long number) {
        if (v) {
            this.oneBits += number
                    * EWAHCompressedBitmap32.WORD_IN_BITS;
        }
    }

    /**
     * virtually add several negated literal words.
     *
     * @param data   the literal words
     * @param start  the starting point in the array
     * @param number the number of literal words to add
     */
    @Override
    public void addStreamOfNegatedLiteralWords(int[] data, int start,
                                               int number) {
        for (int i = start; i < start + number; i++) {
            addLiteralWord(~data[i]);
        }
    }

    @Override
    public void clear() {
        this.oneBits = 0;
    }


    /**
     * As you act on this class, it records the number of set (true) bits.
     *
     * @return number of set bits
     */
    public long getCount() {
        return this.oneBits;
    }

    @Override
    public void setSizeInBitsWithinLastWord(long bits) {
        // no action
    }

    private long oneBits;

}
