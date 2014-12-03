package com.googlecode.javaewah32;

/*
 * Copyright 2009-2014, Daniel Lemire, Cliff Moon, David McIntosh, Robert Becho, Google Inc., Veronika Zenz, Owen Kaser, Gregory Ssi-Yan-Kai, Rory Graves
 * Licensed under the Apache License, Version 2.0.
 */

import com.googlecode.javaewah.ChunkIterator;
import com.googlecode.javaewah.IntIterator;
import com.googlecode.javaewah.LogicalElement;
import com.googlecode.javaewah32.symmetric.RunningBitmapMerge32;
import com.googlecode.javaewah32.symmetric.ThresholdFuncBitmap32;

import java.io.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * <p>
 * This implements the patent-free EWAH scheme. Roughly speaking, it is a 32-bit
 * variant of the BBC compression scheme used by Oracle for its bitmap indexes.
 * </p>
 * 
 * <p>
 * In contrast with the 64-bit EWAH scheme (javaewah.EWAHCompressedBitmap), you
 * can expect this class to compress better, but to be slower at processing the
 * data. In effect, there is a trade-off between memory usage and performances.
 * </p>
 * 
 * <p>Here is a code sample to illustrate usage:</p>
 * <pre>
 * EWAHCompressedBitmap32 ewahBitmap1 = EWAHCompressedBitmap32.bitmapOf(0, 2, 55, 64,
 *         1 &lt;&lt; 30);
 * EWAHCompressedBitmap32 ewahBitmap2 = EWAHCompressedBitmap32.bitmapOf(1, 3, 64,
 *         1 &lt;&lt; 30);
 * EWAHCompressedBitmap32 ewahBitmap3 = EWAHCompressedBitmap32
 *         .bitmapOf(5, 55, 1 &lt;&lt; 30);
 * EWAHCompressedBitmap32 ewahBitmap4 = EWAHCompressedBitmap32
 *         .bitmapOf(4, 66, 1 &lt;&lt; 30);
 * EWAHCompressedBitmap32 orBitmap = ewahBitmap1.or(ewahBitmap2);
 * EWAHCompressedBitmap32 andbitmap = ewahBitmap1.and(ewahBitmap2);
 * EWAHCompressedBitmap32 xorbitmap = ewahBitmap1.xor(ewahBitmap2);
 * andbitmap = EWAHCompressedBitmap32.and(ewahBitmap1, ewahBitmap2, ewahBitmap3,
 *         ewahBitmap4);
 * ByteArrayOutputStream bos = new ByteArrayOutputStream();
 * ObjectOutputStream oo = new ObjectOutputStream(bos);
 * ewahBitmap1.writeExternal(oo);
 * oo.close();
 * ewahBitmap1 = null;
 * ewahBitmap1 = new EWAHCompressedBitmap32();
 * ByteArrayInputStream bis = new ByteArrayInputStream(bos.toByteArray());
 * ewahBitmap1.readExternal(new ObjectInputStream(bis));
 * EWAHCompressedBitmap32 threshold2 = EWAHCompressedBitmap32.threshold(2,
 *         ewahBitmap1, ewahBitmap2, ewahBitmap3, ewahBitmap4);
 * </pre>
 * 
 * <p>
 * The objective of this compression type is to provide some compression, while
 * reducing as much as possible the CPU cycle usage.
 * </p>
 * 
 * <p>
 * Once constructed, the bitmap is essentially immutable (unless you call the
 * "set" or "add" methods). Thus, it can be safely used in multi-threaded
 * programs.
 * </p>
 * <p>
 * For more details, see the following papers:
 * </p>
 * 
 * <ul>
 * <li>Daniel Lemire, Owen Kaser, Kamel Aouiche, <a
 * href="http://arxiv.org/abs/0901.3751">Sorting improves word-aligned bitmap
 * indexes</a>. Data &amp; Knowledge Engineering 69 (1), pages 3-28, 2010.</li>
 * <li> Owen Kaser and Daniel Lemire, Compressed bitmap indexes: beyond unions and intersections
 * <a href="http://arxiv.org/abs/1402.4466">http://arxiv.org/abs/1402.4466</a></li>
 * </ul>
 *
 * @see com.googlecode.javaewah.EWAHCompressedBitmap EWAHCompressedBitmap
 * @since 0.5.0
 */
public final class EWAHCompressedBitmap32 implements Cloneable, Externalizable,
        Iterable<Integer>, BitmapStorage32,
        LogicalElement<EWAHCompressedBitmap32> {

    /**
     * Creates an empty bitmap (no bit set to true).
     */
    public EWAHCompressedBitmap32() {
        this.buffer = new IntArray();
        this.rlw = new RunningLengthWord32(this.buffer, 0);
    }

    /**
     * Sets explicitly the buffer size (in 32-bit words). The initial memory
     * usage will be "bufferSize * 32". For large poorly compressible
     * bitmaps, using large values may improve performance.
     *
     * If the requested bufferSize is less than 1, a value of 1 is used
     * by default. In particular, negative values of bufferSize are 
     * effectively ignored.
     *
     * @param bufferSize number of 32-bit words reserved when the object is
     *                   created)
     */
    public EWAHCompressedBitmap32(int bufferSize) {
        this.buffer = new IntArray(bufferSize);
        this.rlw = new RunningLengthWord32(this.buffer, 0);

    }

    /**
     * @param newData the word
     * @deprecated use addWord() instead.
     */
    @Deprecated
    public void add(final int newData) {
        addWord(newData);
    }

    /**
     * @param newData        the word
     * @param bitsThatMatter the number of significant bits (by default it should
     *                       be 64)
     * @deprecated use addWord() instead.
     */
    @Deprecated
    public void add(final int newData, final int bitsThatMatter) {
        addWord(newData, bitsThatMatter);
    }


    /**
     * Adding words directly to the bitmap (for expert use).
     * 
     * This method adds bits in words of 4*8 bits. It is not to
     * be confused with the set method which sets individual bits.
     * 
     * Most users will want the set method.
     * 
     * Example: if you add word 321 to an empty bitmap, you are have
     * added (in binary notation) 0b101000001, so you have effectively
     * called set(0), set(6), set(8) in sequence.
     * 
     * Since this modifies the bitmap, this method is not thread-safe.
     * 
     * API change: prior to version 0.8.3, this method was called add.
     *
     * @param newData the word
     */
    @Override
    public void addWord(final int newData) {
        addWord(newData, WORD_IN_BITS);
    }

    /**
     * Adding words directly to the bitmap (for expert use). Since this
     * modifies the bitmap, this method is not thread-safe.
     * 
     * API change: prior to version 0.8.3, this method was called add.
     *
     * @param newData        the word
     * @param bitsThatMatter the number of significant bits (by default it should
     *                       be 32)
     */
    public void addWord(final int newData, final int bitsThatMatter) {
        this.sizeInBits += bitsThatMatter;
        if (newData == 0) {
            insertEmptyWord(false);
        } else if (newData == ~0) {
            insertEmptyWord(true);
        } else {
            insertLiteralWord(newData);
        }
    }

    /**
     * For internal use.
     *
     * @param v the boolean value
     */
    private void insertEmptyWord(final boolean v) {
        final boolean noliteralword = (this.rlw
                .getNumberOfLiteralWords() == 0);
        final int runlen = this.rlw.getRunningLength();
        if ((noliteralword) && (runlen == 0)) {
            this.rlw.setRunningBit(v);
        }
        if ((noliteralword)
                && (this.rlw.getRunningBit() == v)
                && (runlen < RunningLengthWord32.LARGEST_RUNNING_LENGTH_COUNT)) {
            this.rlw.setRunningLength(runlen + 1);
            return;
        }
        this.buffer.push_back(0);
        this.rlw.position = this.buffer.sizeInWords() - 1;
        this.rlw.setRunningBit(v);
        this.rlw.setRunningLength(1);
    }

    /**
     * Adding literal words directly to the bitmap (for expert use).
     * Since this modifies the bitmap, this method is not thread-safe.
     *
     * @param newData the literal word
     */
    @Override
    public void addLiteralWord(final int newData) {
        this.sizeInBits += WORD_IN_BITS;
        insertLiteralWord(newData);
    }
    
    /**
     * For internal use.
     *
     * @param newData the literal word
     */
    private void insertLiteralWord(final int newData) {
        final int numberSoFar = this.rlw.getNumberOfLiteralWords();
        if (numberSoFar >= RunningLengthWord32.LARGEST_LITERAL_COUNT) {
            this.buffer.push_back(0);
            this.rlw.position = this.buffer.sizeInWords() - 1;
            this.rlw.setNumberOfLiteralWords(1);
            this.buffer.push_back(newData);
        }
        this.rlw.setNumberOfLiteralWords(numberSoFar + 1);
        this.buffer.push_back(newData);
    }

    /**
     * if you have several literal words to copy over, this might be faster.
     * 
     * Since this modifies the bitmap, this method is not thread-safe.
     *
     * @param data   the literal words
     * @param start  the starting point in the array
     * @param number the number of literal words to add
     */
    @Override
    public void addStreamOfLiteralWords(final int[] data, final int start,
                                        final int number) {
        int leftOverNumber = number;
        while (leftOverNumber > 0) {
            final int numberOfLiteralWords = this.rlw.getNumberOfLiteralWords();
            final int whatWeCanAdd = leftOverNumber < RunningLengthWord32.LARGEST_LITERAL_COUNT
                    - numberOfLiteralWords ? leftOverNumber
                    : RunningLengthWord32.LARGEST_LITERAL_COUNT
                    - numberOfLiteralWords;
            this.rlw.setNumberOfLiteralWords(numberOfLiteralWords + whatWeCanAdd);
            leftOverNumber -= whatWeCanAdd;
            this.buffer.push_back(data, start, whatWeCanAdd);
            this.sizeInBits += whatWeCanAdd * WORD_IN_BITS;
            if (leftOverNumber > 0) {
                this.buffer.push_back(0);
                this.rlw.position = this.buffer.sizeInWords() - 1;
            }
        }
    }

    /**
     * For experts: You want to add many zeroes or ones? This is the method
     * you use.
     * 
     * Since this modifies the bitmap, this method is not thread-safe.
     *
     * @param v      the boolean value
     * @param number the number
     */
    @Override
    public void addStreamOfEmptyWords(final boolean v, int number) {
        if (number == 0)
            return;
        this.sizeInBits += number * WORD_IN_BITS;
        fastaddStreamOfEmptyWords(v, number);
    }

    /**
     * Same as addStreamOfLiteralWords, but the words are negated.
     * 
     * Since this modifies the bitmap, this method is not thread-safe.
     *
     * @param data   the literal words
     * @param start  the starting point in the array
     * @param number the number of literal words to add
     */
    @Override
    public void addStreamOfNegatedLiteralWords(final int[] data,
                                               final int start, final int number) {
        int leftOverNumber = number;
        while (leftOverNumber > 0) {
            final int NumberOfLiteralWords = this.rlw
                    .getNumberOfLiteralWords();
            final int whatwecanadd = leftOverNumber < RunningLengthWord32.LARGEST_LITERAL_COUNT
                    - NumberOfLiteralWords ? leftOverNumber
                    : RunningLengthWord32.LARGEST_LITERAL_COUNT
                    - NumberOfLiteralWords;
            this.rlw.setNumberOfLiteralWords(NumberOfLiteralWords
                    + whatwecanadd);
            leftOverNumber -= whatwecanadd;
            this.buffer.negative_push_back(data, start, whatwecanadd);
            this.sizeInBits += whatwecanadd * WORD_IN_BITS;
            if (leftOverNumber > 0) {
                this.buffer.push_back(0);
                this.rlw.position = this.buffer.sizeInWords() - 1;
            }
        }
    }

    /**
     * Returns a new compressed bitmap containing the bitwise AND values of
     * the current bitmap with some other bitmap. The current bitmap
     * is not modified.
     * 
     * The running time is proportional to the sum of the compressed sizes
     * (as reported by sizeInBytes()).
     * 
     * If you are not planning on adding to the resulting bitmap, you may
     * call the trim() method to reduce memory usage.
     *
     * @param a the other bitmap (it will not be modified)
     * @return the EWAH compressed bitmap
     */
    @Override
    public EWAHCompressedBitmap32 and(final EWAHCompressedBitmap32 a) {
        int size = this.buffer.sizeInWords() > a.buffer.sizeInWords() ? this.buffer.sizeInWords()
                : a.buffer.sizeInWords();
        final EWAHCompressedBitmap32 container = new EWAHCompressedBitmap32(size);
        andToContainer(a, container);
        return container;
    }

    /**
     * Computes new compressed bitmap containing the bitwise AND values of
     * the current bitmap with some other bitmap.
     * The current bitmap is not modified.
     * 
     * The running time is proportional to the sum of the compressed sizes
     * (as reported by sizeInBytes()).
     * 
     * The content of the container is overwritten.
     *
     * @param a         the other bitmap  (it will not be modified)
     * @param container where we store the result
     * @since 0.4.0
     */
    public void andToContainer(final EWAHCompressedBitmap32 a,
                               final BitmapStorage32 container) {
        container.clear();
        final EWAHIterator32 i = a.getEWAHIterator();
        final EWAHIterator32 j = getEWAHIterator();
        final IteratingBufferedRunningLengthWord32 rlwi = new IteratingBufferedRunningLengthWord32(
                i);
        final IteratingBufferedRunningLengthWord32 rlwj = new IteratingBufferedRunningLengthWord32(
                j);
        while ((rlwi.size() > 0) && (rlwj.size() > 0)) {
            while ((rlwi.getRunningLength() > 0)
                    || (rlwj.getRunningLength() > 0)) {
                final boolean i_is_prey = rlwi
                        .getRunningLength() < rlwj
                        .getRunningLength();
                final IteratingBufferedRunningLengthWord32 prey = i_is_prey ? rlwi
                        : rlwj;
                final IteratingBufferedRunningLengthWord32 predator = i_is_prey ? rlwj
                        : rlwi;
                if (!predator.getRunningBit()) {
                    container.addStreamOfEmptyWords(false,
                            predator.getRunningLength());
                    prey.discardFirstWords(predator
                            .getRunningLength());
                } else {
                    final int index = prey.discharge(
                            container,
                            predator.getRunningLength());
                    container.addStreamOfEmptyWords(false,
                            predator.getRunningLength()
                                    - index
                    );
                }
                predator.discardRunningWords();
            }
            final int nbre_literal = Math.min(
                    rlwi.getNumberOfLiteralWords(),
                    rlwj.getNumberOfLiteralWords());
            if (nbre_literal > 0) {
                for (int k = 0; k < nbre_literal; ++k)
                    container.addLiteralWord(rlwi.getLiteralWordAt(k)
                            & rlwj.getLiteralWordAt(k));
                rlwi.discardFirstWords(nbre_literal);
                rlwj.discardFirstWords(nbre_literal);
            }
        }
        if (ADJUST_CONTAINER_SIZE_WHEN_AGGREGATING) {
            final boolean i_remains = rlwi.size() > 0;
            final IteratingBufferedRunningLengthWord32 remaining = i_remains ? rlwi
                    : rlwj;
            remaining.dischargeAsEmpty(container);
            container.setSizeInBitsWithinLastWord(Math.max(sizeInBits(),
                    a.sizeInBits()));
        }
    }

    /**
     * Returns the cardinality of the result of a bitwise AND of the values
     * of the current bitmap with some other bitmap. Avoids
     * allocating an intermediate bitmap to hold the result of the OR.
     * The current bitmap is not modified.
     *
     * @param a the other bitmap  (it will not be modified)
     * @return the cardinality
     */
    public int andCardinality(final EWAHCompressedBitmap32 a) {
        final BitCounter32 counter = new BitCounter32();
        andToContainer(a, counter);
        return counter.getCount();
    }

    /**
     * Returns a new compressed bitmap containing the bitwise AND NOT values
     * of the current bitmap with some other bitmap. The current bitmap
     * is not modified.
     * 
     * The running time is proportional to the sum of the compressed sizes
     * (as reported by sizeInBytes()).
     * 
     * If you are not planning on adding to the resulting bitmap, you may
     * call the trim() method to reduce memory usage.
     *
     * @param a the other bitmap  (it will not be modified)
     * @return the EWAH compressed bitmap
     */
    @Override
    public EWAHCompressedBitmap32 andNot(final EWAHCompressedBitmap32 a) {
        int size = this.buffer.sizeInWords() > a.buffer.sizeInWords() ? this.buffer.sizeInWords()
                : a.buffer.sizeInWords();
        final EWAHCompressedBitmap32 container = new EWAHCompressedBitmap32(size);
        andNotToContainer(a, container);
        return container;
    }

    /**
     * Returns a new compressed bitmap containing the bitwise AND NOT values
     * of the current bitmap with some other bitmap. The current bitmap
     * is not modified.
     * 
     * The running time is proportional to the sum of the compressed sizes
     * (as reported by sizeInBytes()).
     * 
     * The content of the container is overwritten.
     *
     * @param a         the other bitmap  (it will not be modified)
     * @param container where we store the result
     */
    public void andNotToContainer(final EWAHCompressedBitmap32 a,
                                  final BitmapStorage32 container) {
        container.clear();
        final EWAHIterator32 i = getEWAHIterator();
        final EWAHIterator32 j = a.getEWAHIterator();
        final IteratingBufferedRunningLengthWord32 rlwi = new IteratingBufferedRunningLengthWord32(
                i);
        final IteratingBufferedRunningLengthWord32 rlwj = new IteratingBufferedRunningLengthWord32(
                j);
        while ((rlwi.size() > 0) && (rlwj.size() > 0)) {
            while ((rlwi.getRunningLength() > 0)
                    || (rlwj.getRunningLength() > 0)) {
                final boolean i_is_prey = rlwi
                        .getRunningLength() < rlwj
                        .getRunningLength();
                final IteratingBufferedRunningLengthWord32 prey = i_is_prey ? rlwi
                        : rlwj;
                final IteratingBufferedRunningLengthWord32 predator = i_is_prey ? rlwj
                        : rlwi;
                if (((predator.getRunningBit()) && (i_is_prey))
                        || ((!predator.getRunningBit()) && (!i_is_prey))) {
                    container.addStreamOfEmptyWords(false,
                            predator.getRunningLength());
                    prey.discardFirstWords(predator
                            .getRunningLength());
                } else if (i_is_prey) {
                    final int index = prey.discharge(container,
                            predator.getRunningLength());
                    container.addStreamOfEmptyWords(false,
                            predator.getRunningLength()
                                    - index
                    );
                } else {
                    final int index = prey.dischargeNegated(
                            container,
                            predator.getRunningLength());
                    container.addStreamOfEmptyWords(true,
                            predator.getRunningLength()
                                    - index
                    );
                }
                predator.discardRunningWords();
            }
            final int nbre_literal = Math.min(
                    rlwi.getNumberOfLiteralWords(),
                    rlwj.getNumberOfLiteralWords());
            if (nbre_literal > 0) {
                for (int k = 0; k < nbre_literal; ++k)
                    container.addLiteralWord(rlwi.getLiteralWordAt(k)
                            & (~rlwj.getLiteralWordAt(k)));
                rlwi.discardFirstWords(nbre_literal);
                rlwj.discardFirstWords(nbre_literal);
            }
        }
        final boolean i_remains = rlwi.size() > 0;
        final IteratingBufferedRunningLengthWord32 remaining = i_remains ? rlwi : rlwj;
        if (i_remains)
            remaining.discharge(container);
        else if (ADJUST_CONTAINER_SIZE_WHEN_AGGREGATING)
            remaining.dischargeAsEmpty(container);
        if (ADJUST_CONTAINER_SIZE_WHEN_AGGREGATING)
            container.setSizeInBitsWithinLastWord(Math.max(sizeInBits(),
                    a.sizeInBits()));

    }

    /**
     * Returns the cardinality of the result of a bitwise AND NOT of the
     * values of the current bitmap with some other bitmap. Avoids allocating
     * an intermediate bitmap to hold the result of the OR.
     * The current bitmap is not modified.
     *
     * @param a the other bitmap  (it will not be modified)
     * @return the cardinality
     */
    public int andNotCardinality(final EWAHCompressedBitmap32 a) {
        final BitCounter32 counter = new BitCounter32();
        andNotToContainer(a, counter);
        return counter.getCount();
    }

    /**
     * reports the number of bits set to true. Running time is proportional
     * to compressed size (as reported by sizeInBytes).
     *
     * @return the number of bits set to true
     */
    public int cardinality() {
        int counter = 0;
        final EWAHIterator32 i = this.getEWAHIterator();
        while (i.hasNext()) {
            RunningLengthWord32 localrlw = i.next();
            if (localrlw.getRunningBit()) {
                counter += WORD_IN_BITS
                        * localrlw.getRunningLength();
            }
            for (int j = 0; j < localrlw.getNumberOfLiteralWords(); ++j) {
                counter += Integer.bitCount(i.buffer().getWord(i
                        .literalWords() + j));
            }
        }
        return counter;
    }

    /**
     * Clear any set bits and set size in bits back to 0
     */
    @Override
    public void clear() {
        this.sizeInBits = 0;
        this.buffer.clear();
        this.rlw.position = 0;
    }

    /*
     * @see java.lang.Object#clone()
     */
    @Override
    public EWAHCompressedBitmap32 clone() {
        EWAHCompressedBitmap32 clone = null;
        try {
            clone = (EWAHCompressedBitmap32) super
                    .clone();
            clone.buffer = this.buffer.clone();
            clone.sizeInBits = this.sizeInBits;
            clone.rlw = new RunningLengthWord32(clone.buffer, this.rlw.position);
        } catch (CloneNotSupportedException e) {
            e.printStackTrace(); // cannot happen
        }
        return clone;
    }

    /**
     * Serialize.
     * 
     * The current bitmap is not modified.
     *
     * @param out the ObjectOutput stream
     * @throws IOException Signals that an I/O exception has occurred.
     */
    public void serialize(ObjectOutput out) throws IOException {
        out.writeInt(this.sizeInBits);
        out.writeObject(this.buffer);
        out.writeInt(this.rlw.position);
    }
    
    /**
     * Deserialize.
     *
     * @param in the ObjectInput stream
     * @throws IOException Signals that an I/O exception has occurred.
     * @throws ClassNotFoundException
     */
    public void deserialize(ObjectInput in) throws IOException, ClassNotFoundException {
        this.sizeInBits = in.readInt();
        this.buffer = (Buffer) in.readObject();
        this.rlw = new RunningLengthWord32(this.buffer, in.readInt());
    }

    /**
     * Check to see whether the two compressed bitmaps contain the same set
     * bits.
     *
     * @see java.lang.Object#equals(java.lang.Object)
     */
    @Override
    public boolean equals(Object o) {
        if (o instanceof EWAHCompressedBitmap32) {
            try {
                this.xorToContainer((EWAHCompressedBitmap32) o,
                        new NonEmptyVirtualStorage32());
                return true;
            } catch (NonEmptyVirtualStorage32.NonEmptyException e) {
                return false;
            }
        }
        return false;
    }

    /**
     * For experts: You want to add many zeroes or ones faster?
     * 
     * This method does not update sizeInBits.
     *
     * @param v      the boolean value
     * @param number the number (must be greater than 0)
     */
    private void fastaddStreamOfEmptyWords(final boolean v, int number) {
        if ((this.rlw.getRunningBit() != v) && (this.rlw.size() == 0)) {
            this.rlw.setRunningBit(v);
        } else if ((this.rlw.getNumberOfLiteralWords() != 0)
                || (this.rlw.getRunningBit() != v)) {
            this.buffer.push_back(0);
            this.rlw.position = this.buffer.sizeInWords() - 1;
            if (v)
                this.rlw.setRunningBit(true);
        }
        final int runLen = this.rlw.getRunningLength();
        final int whatWeCanAdd = number < RunningLengthWord32.LARGEST_RUNNING_LENGTH_COUNT
                - runLen ? number
                : RunningLengthWord32.LARGEST_RUNNING_LENGTH_COUNT
                - runLen;
        this.rlw.setRunningLength(runLen + whatWeCanAdd);
        number -= whatWeCanAdd;
        while (number >= RunningLengthWord32.LARGEST_RUNNING_LENGTH_COUNT) {
            this.buffer.push_back(0);
            this.rlw.position = this.buffer.sizeInWords() - 1;
            if (v)
                this.rlw.setRunningBit(true);
            this.rlw.setRunningLength(RunningLengthWord32.LARGEST_RUNNING_LENGTH_COUNT);
            number -= RunningLengthWord32.LARGEST_RUNNING_LENGTH_COUNT;
        }
        if (number > 0) {
            this.buffer.push_back(0);
            this.rlw.position = this.buffer.sizeInWords() - 1;
            if (v)
                this.rlw.setRunningBit(true);
            this.rlw.setRunningLength(number);
        }
    }

    /**
     * Gets an EWAHIterator32 over the data. This is a customized iterator
     * which iterates over run length words. For experts only.
     * 
     * The current bitmap is not modified.
     *
     * @return the EWAHIterator32
     */
    public EWAHIterator32 getEWAHIterator() {
        return new EWAHIterator32(this.buffer);
    }

    /**
     * Gets a ReverseEWAHIterator32 over the data. This is a customized iterator
     * which iterates over run length words in reverse order. For experts only.
     *
     * The current bitmap is not modified.
     *
     * @return the ReverseEWAHIterator32
     */
    private ReverseEWAHIterator32 getReverseEWAHIterator() {
        return new ReverseEWAHIterator32(this.buffer);
    }

    /**
     * Gets an IteratingRLW to iterate over the data. For experts only.
     * 
     * Note that iterator does not know about the size in bits of the
     * bitmap: the size in bits is effectively rounded up to the nearest
     * multiple of 32. However, if you materialize a bitmap from 
     * an iterator, you can set the desired size in bits using the
     * setSizeInBitsWithinLastWord methods:
     * 
     *  <code>
     *  EWAHCompressedBitmap32 n = IteratorUtil32.materialize(bitmap.getIteratingRLW()));
     *  n.setSizeInBitsWithinLastWord(bitmap.sizeInBits());
     *  </code>

     * 
     * The current bitmap is not modified.
     *
     * @return the IteratingRLW iterator corresponding to this bitmap
     */
    public IteratingRLW32 getIteratingRLW() {
        return new IteratingBufferedRunningLengthWord32(this);
    }

    /**
     * @return a list
     * @deprecated use toList() instead.
     */
    @Deprecated
    public List<Integer> getPositions() {
        return toList();
    }

    /**
     * Gets the locations of the true values as one list. (May use more
     * memory than iterator().)
     * 
     * The current bitmap is not modified.
     * 
     * API change: prior to version 0.8.3, this method was called getPositions.
     *
     * @return the positions
     */
    public List<Integer> toList() {
        final ArrayList<Integer> v = new ArrayList<Integer>();
        final EWAHIterator32 i = this.getEWAHIterator();
        int pos = 0;
        while (i.hasNext()) {
            RunningLengthWord32 localrlw = i.next();
            if (localrlw.getRunningBit()) {
                for (int j = 0; j < localrlw.getRunningLength(); ++j) {
                    for (int c = 0; c < WORD_IN_BITS; ++c)
                        v.add(pos++);
                }
            } else {
                pos += WORD_IN_BITS * localrlw.getRunningLength();
            }
            for (int j = 0; j < localrlw.getNumberOfLiteralWords(); ++j) {
                int data = i.buffer().getWord(i.literalWords() + j);
                while (data != 0) {
                    final int T = data & -data;
                    v.add(Integer.bitCount(T - 1)
                            + pos);
                    data ^= T;
                }
                pos += WORD_IN_BITS;
            }
        }
        while ((v.size() > 0)
                && (v.get(v.size() - 1) >= this.sizeInBits))
            v.remove(v.size() - 1);
        return v;
    }

    /**
     * Returns a customized hash code (based on Karp-Rabin). Naturally, if
     * the bitmaps are equal, they will hash to the same value.
     * 
     * The current bitmap is not modified.
     */
    @Override
    public int hashCode() {
        int karprabin = 0;
        final int B = 31;
        final EWAHIterator32 i = this.getEWAHIterator();
        while (i.hasNext()) {
            i.next();
            if (i.rlw.getRunningBit()) {
                karprabin += B * karprabin
                        + i.rlw.getRunningLength();
            }
            for (int k = 0; k < i.rlw.getNumberOfLiteralWords(); ++k) {
                karprabin += B * karprabin
                        + this.buffer.getWord(k + i.literalWords());
            }
        }
        return karprabin;
    }

    /**
     * Return true if the two EWAHCompressedBitmap have both at least one
     * true bit in the same position. Equivalently, you could call "and" and
     * check whether there is a set bit, but intersects will run faster if
     * you don't need the result of the "and" operation.
     * 
     * The current bitmap is not modified.
     *
     * @param a the other bitmap (it will not be modified)
     * @return whether they intersect
     */
    public boolean intersects(final EWAHCompressedBitmap32 a) {
        NonEmptyVirtualStorage32 nevs = new NonEmptyVirtualStorage32();
        try {
            this.andToContainer(a, nevs);
        } catch (NonEmptyVirtualStorage32.NonEmptyException nee) {
            return true;
        }
        return false;
    }

    /**
     * Iterator over the set bits (this is what most people will want to use
     * to browse the content if they want an iterator). The location of the
     * set bits is returned, in increasing order.
     * 
     * The current bitmap is not modified.
     *
     * @return the int iterator
     */
    public IntIterator intIterator() {
        return new IntIteratorImpl32(this.getEWAHIterator());
    }

    /**
     * Iterator over the set bits in reverse order.
     *
     * The current bitmap is not modified.
     *
     * @return the int iterator
     */
    public IntIterator reverseIntIterator() {
        return new ReverseIntIterator32(this.getReverseEWAHIterator(), this.sizeInBits);
    }

    /**
     * Checks whether this bitmap is empty (has a cardinality of zero).
     * 
     * @return true if no bit is set
     */
    public boolean isEmpty() {
       return getFirstSetBit() < 0;
    }

    /**
     * Iterator over the clear bits. The location of the clear bits is
     * returned, in increasing order.
     * 
     * The current bitmap is not modified.
     *
     * @return the int iterator
     */
    public IntIterator clearIntIterator() {
        return new ClearIntIterator32(this.getEWAHIterator(), this.sizeInBits);
    }

    /**
     * Iterator over the chunk of bits.
     *
     * The current bitmap is not modified.
     *
     * @return the chunk iterator
     */
    public ChunkIterator chunkIterator() {
        return new ChunkIteratorImpl32(this.getEWAHIterator(), this.sizeInBits);
    }

    /**
     * Iterates over the positions of the true values. This is similar to
     * intIterator(), but it uses Java generics.
     * 
     * The current bitmap is not modified.
     *
     * @return the iterator
     */
    @Override
    public Iterator<Integer> iterator() {
        return new Iterator<Integer>() {
            @Override
            public boolean hasNext() {
                return this.under.hasNext();
            }

            @Override
            public Integer next() {
                return this.under.next();
            }

            @Override
            public void remove() {
                throw new UnsupportedOperationException(
                        "bitsets do not support remove");
            }

            private final IntIterator under = intIterator();
        };
    }

    /**
     * Negate (bitwise) the current bitmap. To get a negated copy, do
     * EWAHCompressedBitmap x= ((EWAHCompressedBitmap) mybitmap.clone());
     * x.not();
     * 
     * The running time is proportional to the compressed size (as reported
     * by sizeInBytes()).
     * 
     * Because this method modifies the bitmap, it is not thread-safe.
     */
    @Override
    public void not() {
        final EWAHIterator32 i = this.getEWAHIterator();
        if (!i.hasNext())
            return;
        while (true) {
            final RunningLengthWord32 rlw1 = i.next();
            rlw1.setRunningBit(!rlw1.getRunningBit());
            for (int j = 0; j < rlw1.getNumberOfLiteralWords(); ++j) {
                i.buffer().negateWord(i.literalWords() + j);
            }
            if (!i.hasNext()) {// must potentially adjust the last
                // literal word
                final int usedbitsinlast = this.sizeInBits
                        % WORD_IN_BITS;
                if (usedbitsinlast == 0)
                    return;
                if (rlw1.getNumberOfLiteralWords() == 0) {
                    if ((rlw1.getRunningLength() > 0)
                            && (rlw1.getRunningBit())) {
						if ((rlw1.getRunningLength() == 1)
								&& (rlw1.position > 0)) {
							// we need to prune ending
							final EWAHIterator32 j = this.getEWAHIterator();
							int newrlwpos = this.rlw.position;
							while (j.hasNext()) {
								RunningLengthWord32 r = j.next();
								if (r.position < rlw1.position) {
									newrlwpos = r.position;
								} else break;
							}
							this.rlw.position = newrlwpos;
							this.buffer.removeLastWord();
						} else
							rlw1.setRunningLength(rlw1.getRunningLength() - 1);
                        this.insertLiteralWord((~0) >>> (WORD_IN_BITS - usedbitsinlast));
                        
                    }
                    return;
                }
                i.buffer().andWord(i.literalWords()
                        + rlw1.getNumberOfLiteralWords() - 1, (~0) >>> (WORD_IN_BITS - usedbitsinlast));
                return;
            }
        }
    }

    /**
     * Returns a new compressed bitmap containing the bitwise OR values of
     * the current bitmap with some other bitmap.
     * 
     * The running time is proportional to the sum of the compressed sizes
     * (as reported by sizeInBytes()).
     * 
     * If you are not planning on adding to the resulting bitmap, you may
     * call the trim() method to reduce memory usage.
     * 
     * The current bitmap is not modified.
     *
     * @param a the other bitmap (it will not be modified)
     * @return the EWAH compressed bitmap
     */
    @Override
    public EWAHCompressedBitmap32 or(final EWAHCompressedBitmap32 a) {
    	int size = this.buffer.sizeInWords() + a.buffer.sizeInWords();
        final EWAHCompressedBitmap32 container = new EWAHCompressedBitmap32(size);
        orToContainer(a, container);
        return container;
    }

    /**
     * Computes the bitwise or between the current bitmap and the bitmap
     * "a". Stores the result in the container.
     * 
     * The current bitmap is not modified.
     * 
     * The content of the container is overwritten.
     *
     * @param a         the other bitmap (it will not be modified)
     * @param container where we store the result
     */
    public void orToContainer(final EWAHCompressedBitmap32 a,
                              final BitmapStorage32 container) {
        container.clear();
        final EWAHIterator32 i = a.getEWAHIterator();
        final EWAHIterator32 j = getEWAHIterator();
        final IteratingBufferedRunningLengthWord32 rlwi = new IteratingBufferedRunningLengthWord32(
                i);
        final IteratingBufferedRunningLengthWord32 rlwj = new IteratingBufferedRunningLengthWord32(
                j);
        while ((rlwi.size() > 0) && (rlwj.size() > 0)) {
            while ((rlwi.getRunningLength() > 0)
                    || (rlwj.getRunningLength() > 0)) {
                final boolean i_is_prey = rlwi
                        .getRunningLength() < rlwj
                        .getRunningLength();
                final IteratingBufferedRunningLengthWord32 prey = i_is_prey ? rlwi
                        : rlwj;
                final IteratingBufferedRunningLengthWord32 predator = i_is_prey ? rlwj
                        : rlwi;
                if (predator.getRunningBit()) {
                    container.addStreamOfEmptyWords(true,
                            predator.getRunningLength());
                    prey.discardFirstWords(predator
                            .getRunningLength());
                } else {
                    final int index = prey.discharge(container,
                            predator.getRunningLength());
                    container.addStreamOfEmptyWords(false,
                            predator.getRunningLength()
                                    - index
                    );
                }
                predator.discardRunningWords();
            }
            final int nbre_literal = Math.min(
                    rlwi.getNumberOfLiteralWords(),
                    rlwj.getNumberOfLiteralWords());
            if (nbre_literal > 0) {
                for (int k = 0; k < nbre_literal; ++k) {
                    container.addLiteralWord(rlwi.getLiteralWordAt(k)
                            | rlwj.getLiteralWordAt(k));
                }
                rlwi.discardFirstWords(nbre_literal);
                rlwj.discardFirstWords(nbre_literal);
            }
        }
        if ((rlwj.size() > 0) && (rlwi.size() > 0)) throw new RuntimeException("fds");
        final boolean i_remains = rlwi.size() > 0;
        final IteratingBufferedRunningLengthWord32 remaining = i_remains ? rlwi
                : rlwj;
        remaining.discharge(container);
        container.setSizeInBitsWithinLastWord(Math.max(sizeInBits(), a.sizeInBits()));
    }

    /**
     * Returns the cardinality of the result of a bitwise OR of the values
     * of the current bitmap with some other bitmap. Avoids allocating
     * an intermediate bitmap to hold the result of the OR.
     * 
     * The current bitmap is not modified.
     *
     * @param a the other bitmap (it will not be modified)
     * @return the cardinality
     */
    public int orCardinality(final EWAHCompressedBitmap32 a) {
        final BitCounter32 counter = new BitCounter32();
        orToContainer(a, counter);
        return counter.getCount();
    }    

    /*
     * @see java.io.Externalizable#readExternal(java.io.ObjectInput)
     */
    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        deserialize(in);
    }
    
    /*
     * @see java.io.Externalizable#writeExternal(java.io.ObjectOutput)
     */
    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        serialize(out);
    }

    /**
     * Report the number of bytes required to serialize this bitmap.
     * 
     * The current bitmap is not modified.
     *
     * @return the size in bytes
     */
    public int serializedSizeInBytes() {
        return this.sizeInBytes() + 3 * 4;
    }

    /**
     * Query the value of a single bit. Relying on this method when speed is
     * needed is discouraged. The complexity is linear with the size of the
     * bitmap.
     * 
     * (This implementation is based on zhenjl's Go version of JavaEWAH.)
     * 
     * The current bitmap is not modified.
     *
     * @param i the bit we are interested in
     * @return whether the bit is set to true
     */
    public boolean get(final int i) {
        if ((i < 0) || (i >= this.sizeInBits))
            return false;
        int wordChecked = 0;
        final IteratingRLW32 j = getIteratingRLW();
        final int wordi = i / WORD_IN_BITS;
        while (wordChecked <= wordi) {
            wordChecked += j.getRunningLength();
            if (wordi < wordChecked) {
                return j.getRunningBit();
            }
            if (wordi < wordChecked + j.getNumberOfLiteralWords()) {
                final int w = j.getLiteralWordAt(wordi
                        - wordChecked);
                return (w & (1 << i)) != 0;
            }
            wordChecked += j.getNumberOfLiteralWords();
            j.next();
        }
        return false;
    }

    /**
     * getFirstSetBit is a light-weight method that returns the
     * location of the set bit (=1) or -1 if there is none.
     * 
     * @return location of the first set bit or -1
     */
    public int getFirstSetBit() {
        int nword = 0;
        for(int pos = 0; pos < this.buffer.sizeInWords(); ++pos) {
           int rl = RunningLengthWord32.getRunningLength(this.buffer, pos);
           boolean rb = RunningLengthWord32.getRunningBit(this.buffer, pos);
           if((rl > 0) && rb) {
                return nword * WORD_IN_BITS;
           }
           nword  += rl;
           int lw = RunningLengthWord32.getNumberOfLiteralWords(this.buffer, pos);
           if(lw > 0) {
               int word = this.buffer.getWord(pos + 1);
               if(word != 0) {
                   int T = word & -word;
                   return nword * WORD_IN_BITS + Integer.bitCount(T - 1);
               }
           }
        }
        return -1;
    }
    
    /**
     * Set the bit at position i to true.
     * 
     * Though you can set the bits in any order (e.g., set(100), set(10), set(1),
     * you will typically get better performance if you set the bits in increasing order (e.g., set(1), set(10), set(100)).
     * 
     * Setting a bit that is larger than any of the current set bit
     * is a constant time operation. Setting a bit that is smaller than an 
     * already set bit can require time proportional to the compressed
     * size of the bitmap, as the bitmap may need to be rewritten.

     * 
     * Since this modifies the bitmap, this method is not thread-safe.
     *
     * @param i the index
     * @return true if the value was set
     * @throws IndexOutOfBoundsException if i is negative or greater than Integer.MAX_VALUE - 32
     */
    public boolean set(final int i) {
        if ((i > Integer.MAX_VALUE - WORD_IN_BITS) || (i < 0))
            throw new IndexOutOfBoundsException(
                    "Set values should be between 0 and "
                            + (Integer.MAX_VALUE - WORD_IN_BITS)
            );
        if (i < this.sizeInBits) {
            locateAndSet(i);
        } else {
            extendAndSet(i);
        }
        return true;
	}

    /**
     * For internal use.
     *
     * @param i the index
     */
    private void extendAndSet(int i) {
        final int dist = distanceInWords(i);
        this.sizeInBits = i + 1;
        if (dist > 0) {
            if (dist > 1) {
                fastaddStreamOfEmptyWords(false, dist - 1);
            }
            insertLiteralWord(1 << (i % WORD_IN_BITS));
        }
        if (this.rlw.getNumberOfLiteralWords() == 0) {
            this.rlw.setRunningLength(this.rlw.getRunningLength() - 1);
            insertLiteralWord(1 << (i % WORD_IN_BITS));
        }
        this.buffer.orLastWord(1 << (i % WORD_IN_BITS));
        if (this.buffer.getLastWord() == ~0) {
            this.buffer.removeLastWord();
            this.rlw.setNumberOfLiteralWords(this.rlw
                    .getNumberOfLiteralWords() - 1);
            // next we add one clean word
            insertEmptyWord(true);
        }
    }

    /**
     * For internal use.
     *
     * @param i the index
     */
    private void locateAndSet(int i) {
        int nbits = 0;
        for(int pos = 0; pos < this.buffer.sizeInWords(); ) {
            int rl = RunningLengthWord32.getRunningLength(this.buffer, pos);
            boolean rb = RunningLengthWord32.getRunningBit(this.buffer, pos);
            int lw = RunningLengthWord32.getNumberOfLiteralWords(this.buffer, pos);
            int rbits = rl * WORD_IN_BITS;
            if(i < nbits + rbits) {
                setInRunningLength(i, nbits, pos, rl, rb, lw);
                return;
            }
            nbits += rbits;
            int lbits = lw * WORD_IN_BITS;
            if(i < nbits + lbits) {
                setInLiteralWords(i, nbits, pos, rl, rb, lw);
                return;
            }
            nbits += lbits;
            pos += lw + 1;
        }
    }

    private void setInRunningLength(int i, int nbits, int pos, int rl, boolean rb, int lw) {
        if(!rb) {
            int wordPosition = (i - nbits) / WORD_IN_BITS + 1;
            int addedWords = (wordPosition==rl) ? 1 : 2;
            this.buffer.expand(pos+1, addedWords);
            int mask = 1 << i % WORD_IN_BITS;
            this.buffer.setWord(pos+1, mask);
            if(this.rlw.position >= pos+1) {
                this.rlw.position += addedWords;
            }
            if(addedWords==1) {
                setRLWInfo(pos, rb, rl-1, lw+1);
            } else {
                setRLWInfo(pos, rb, wordPosition-1, 1);
                setRLWInfo(pos+2, rb, rl-wordPosition, lw);
                if(this.rlw.position == pos) {
                    this.rlw.position += 2;
                }
            }
        }
    }

    private void setInLiteralWords(int i, int nbits, int pos, int rl, boolean rb, int lw) {
        int wordPosition = (i - nbits) / WORD_IN_BITS + 1;
        int mask = 1 << i % WORD_IN_BITS;
        this.buffer.orWord(pos + wordPosition, mask);
        if(this.buffer.getWord(pos + wordPosition) == ~0) {
            boolean canMergeInCurrentRLW = mergeLiteralWordInCurrentRunningLength(rb, rl, wordPosition);
            boolean canMergeInNextRLW = mergeLiteralWordInNextRunningLength(lw, pos, wordPosition);
            if(canMergeInCurrentRLW && canMergeInNextRLW) {
                int nextRl = RunningLengthWord32.getRunningLength(this.buffer, pos + 2);
                int nextLw = RunningLengthWord32.getNumberOfLiteralWords(this.buffer, pos + 2);
                this.buffer.collapse(pos,  2);
                setRLWInfo(pos, true, rl + 1 + nextRl, nextLw);
                if(this.rlw.position >= pos+2) {
                    this.rlw.position -= 2;
                }
            } else if(canMergeInCurrentRLW) {
                this.buffer.collapse(pos + 1, 1);
                setRLWInfo(pos, true, rl+1, lw-1);
                if(this.rlw.position >= pos+2) {
                    this.rlw.position--;
                }
            } else if(canMergeInNextRLW) {
                int nextRLWPos = pos + lw + 1;
                int nextRl = RunningLengthWord32.getRunningLength(this.buffer, nextRLWPos);
                int nextLw = RunningLengthWord32.getNumberOfLiteralWords(this.buffer, nextRLWPos);
                this.buffer.collapse(pos+wordPosition, 1);
                setRLWInfo(pos, rb, rl, lw-1);
                setRLWInfo(pos+wordPosition, true, nextRl+1, nextLw);
                if(this.rlw.position >= nextRLWPos) {
                    this.rlw.position -= lw + 1 - wordPosition;
                }
            } else {
                setRLWInfo(pos, rb, rl, wordPosition-1);
                setRLWInfo(pos+wordPosition, true, 1, lw-wordPosition);
                if(this.rlw.position == pos) {
                    this.rlw.position += wordPosition;
                }
            }
        }
    }

    private boolean mergeLiteralWordInCurrentRunningLength(boolean rb, int rl, int wordPosition) {
        return (rb || rl==0) && wordPosition==1;
    }

    private boolean mergeLiteralWordInNextRunningLength(int lw, int pos, int wordPosition) {
        int nextRLWPos = pos + lw + 1;
        if(lw==wordPosition && nextRLWPos<this.buffer.sizeInWords()) {
            int nextRl = RunningLengthWord32.getRunningLength(this.buffer, nextRLWPos);
            boolean nextRb = RunningLengthWord32.getRunningBit(this.buffer, nextRLWPos);
            return (nextRb || nextRl == 0);
        }
        return false;
    }

    private void setRLWInfo(int pos, boolean rb, int rl, int lw) {
        RunningLengthWord32.setRunningBit(this.buffer, pos, rb);
        RunningLengthWord32.setRunningLength(this.buffer, pos, rl);
        RunningLengthWord32.setNumberOfLiteralWords(this.buffer, pos, lw);
    }

    /**
     * Set the size in bits. This does not change the compressed bitmap.
     */
    @Override
    public void setSizeInBitsWithinLastWord(final int size) {
        if ((size + WORD_IN_BITS - 1) / WORD_IN_BITS != (this.sizeInBits + WORD_IN_BITS - 1) / WORD_IN_BITS)
            throw new RuntimeException(
                    "You can only reduce the size of the bitmap within the scope of the last word. To extend the bitmap, please call setSizeInbits(int,boolean): "
                            + size + " " + this.sizeInBits
            );
        this.sizeInBits = size;
        final int usedBitsInLast = this.sizeInBits
                % WORD_IN_BITS;
        if (usedBitsInLast == 0)
            return;
        if (this.rlw.getNumberOfLiteralWords() == 0) {

            if ((this.rlw.getRunningLength() > 0)
                    && (this.rlw.getRunningBit())) {
                if((this.rlw.getRunningLength() == 1) && (this.rlw.position > 0)) {
                    // we need to prune ending
                    final EWAHIterator32 j = this.getEWAHIterator();
                    int newrlwpos = this.rlw.position;
                    while (j.hasNext()) {
                        RunningLengthWord32 r = j.next();
                        if (r.position < this.rlw.position) {
                            newrlwpos = r.position;
                        } else break;
                    }
                    this.rlw.position = newrlwpos;
                    this.buffer.removeLastWord();
                } else {
                    this.rlw.setRunningLength(this.rlw
                            .getRunningLength() - 1);
                }
                this.insertLiteralWord((~0) >>> (WORD_IN_BITS - usedBitsInLast));
            }
            return;
        }
        this.buffer.andLastWord((~0) >>> (WORD_IN_BITS - usedBitsInLast));
    }

    /**
     * Change the reported size in bits of the *uncompressed* bitmap
     * represented by this compressed bitmap. It may change the underlying
     * compressed bitmap. It is not possible to reduce the sizeInBits, but
     * it can be extended. The new bits are set to false or true depending
     * on the value of defaultValue.
     *
     * This method is not thread-safe.
     *
     * @param size         the size in bits
     * @param defaultValue the default boolean value
     * @return true if the update was possible
     */
    public boolean setSizeInBits(final int size, final boolean defaultValue) {
        if (size <= this.sizeInBits) {
            return false;
        }
        if ((this.sizeInBits % WORD_IN_BITS) != 0) {
            if (!defaultValue) {
                if (this.rlw.getNumberOfLiteralWords() > 0) {
                    final int bitsToAdd = size - this.sizeInBits;
                    final int usedBitsInLast = this.sizeInBits % WORD_IN_BITS;
                    final int freeBitsInLast = WORD_IN_BITS - usedBitsInLast;
                    if (this.buffer.getLastWord() == 0) {
                        this.rlw.setNumberOfLiteralWords(this.rlw.getNumberOfLiteralWords() - 1);
                        this.buffer.removeLastWord();
                        this.sizeInBits -= usedBitsInLast;
                    } else if (usedBitsInLast > 0) {
                        this.sizeInBits += Math.min(bitsToAdd, freeBitsInLast);
                    }
                }
            } else {
                if (this.rlw.getNumberOfLiteralWords() == 0) {
                    this.rlw.setRunningLength(this.rlw.getRunningLength() - 1);
                    insertLiteralWord(0);
                }
                final int maskWidth = Math.min(WORD_IN_BITS - this.sizeInBits % WORD_IN_BITS,
                                               size - this.sizeInBits);
                final int maskShift = this.sizeInBits % WORD_IN_BITS;
                final int mask = ((~0) >>> (WORD_IN_BITS - maskWidth)) << maskShift;
                this.buffer.orLastWord(mask);
                if (this.buffer.getLastWord() == ~0) {
                    this.buffer.removeLastWord();
                    this.rlw.setNumberOfLiteralWords(this.rlw.getNumberOfLiteralWords() - 1);
                    insertEmptyWord(true);
                }
                this.sizeInBits += maskWidth;
            }
        }
        this.addStreamOfEmptyWords(defaultValue,
                (size / WORD_IN_BITS) - (this.sizeInBits / WORD_IN_BITS)
        );
        if (this.sizeInBits < size) {
            final int dist = distanceInWords(size - 1);
            if (dist > 0) {
                insertLiteralWord(0);
            }
            if (defaultValue) {
                final int maskWidth = size - this.sizeInBits;
                final int maskShift = this.sizeInBits % WORD_IN_BITS;
                final int mask = ((~0) >>> (WORD_IN_BITS - maskWidth)) << maskShift;
                this.buffer.orLastWord(mask);
            }
            this.sizeInBits = size;
        }
        return true;
    }

    /**
     * For internal use.
     *
     * @param i the index
     */
    private int distanceInWords(final int i) {
        return (i + WORD_IN_BITS) / WORD_IN_BITS
                - (this.sizeInBits + WORD_IN_BITS - 1) / WORD_IN_BITS;
    }

    /**
     * Returns the size in bits of the *uncompressed* bitmap represented by
     * this compressed bitmap. Initially, the sizeInBits is zero. It is
     * extended automatically when you set bits to true.
     * 
     * The current bitmap is not modified.
     *
     * @return the size in bits
     */
    @Override
    public int sizeInBits() {
        return this.sizeInBits;
    }

    /**
     * Report the *compressed* size of the bitmap (equivalent to memory
     * usage, after accounting for some overhead).
     *
     * @return the size in bytes
     */
    @Override
    public int sizeInBytes() {
        return this.buffer.sizeInWords() * (WORD_IN_BITS / 8);
    }


    /**
     * Compute a Boolean threshold function: bits are true where at least T
     * bitmaps have a true bit.
     *
     * @param t       the threshold
     * @param bitmaps input data
     * @return the aggregated bitmap
     * @since 0.8.2
     */
    public static EWAHCompressedBitmap32 threshold(final int t,
                                                   final EWAHCompressedBitmap32... bitmaps) {
        final EWAHCompressedBitmap32 container = new EWAHCompressedBitmap32();
        thresholdWithContainer(container, t, bitmaps);
        return container;
    }

    /**
     * Compute a Boolean threshold function: bits are true where at least T
     * bitmaps have a true bit.
     * 
     * The content of the container is overwritten.
     *
     * @param t         the threshold
     * @param bitmaps   input data
     * @param container where we write the aggregated bitmap
     * @since 0.8.2
     */
    public static void thresholdWithContainer(
            final BitmapStorage32 container, final int t,
            final EWAHCompressedBitmap32... bitmaps) {
        (new RunningBitmapMerge32()).symmetric(
                new ThresholdFuncBitmap32(t), container, bitmaps);
    }


    /**
     * Populate an array of (sorted integers) corresponding to the location
     * of the set bits.
     *
     * @return the array containing the location of the set bits
     */
    public int[] toArray() {
        int[] ans = new int[this.cardinality()];
        int inanspos = 0;
        int pos = 0;
        final EWAHIterator32 i = this.getEWAHIterator();
        while (i.hasNext()) {
            RunningLengthWord32 localrlw = i.next();
            if (localrlw.getRunningBit()) {
                for (int j = 0; j < localrlw.getRunningLength(); ++j) {
                    for (int c = 0; c < WORD_IN_BITS; ++c) {
                        ans[inanspos++] = pos++;
                    }
                }
            } else {
                pos += WORD_IN_BITS * localrlw.getRunningLength();
            }
            for (int j = 0; j < localrlw.getNumberOfLiteralWords(); ++j) {
                int data = i.buffer().getWord(i.literalWords() + j);

                while (data != 0) {
                    final int t = data & -data;
                    ans[inanspos++] = Integer.bitCount(t - 1) + pos;
                    data ^= t;
                }
                pos += WORD_IN_BITS;

            }
        }
        return ans;

    }

    /**
     * A more detailed string describing the bitmap (useful for debugging).
     *
     * @return the string
     */
    public String toDebugString() {
        StringBuffer sb = new StringBuffer(" EWAHCompressedBitmap, size in bits = ");
        sb.append(this.sizeInBits).append(" size in words = ");
        sb.append(this.buffer.sizeInWords()).append("\n");
        final EWAHIterator32 i = this.getEWAHIterator();
        while (i.hasNext()) {
            RunningLengthWord32 localrlw = i.next();
            if (localrlw.getRunningBit()) {
                sb.append(localrlw.getRunningLength()).append(" 1x11\n");
            } else {
                sb.append(localrlw.getRunningLength()).append(" 0x00\n");
            }
            sb.append(localrlw.getNumberOfLiteralWords()).append(" dirties\n");
            for (int j = 0; j < localrlw.getNumberOfLiteralWords(); ++j) {
                int data = i.buffer().getWord(i.literalWords() + j);
                sb.append("\t").append(data).append("\n");
            }
        }
        return sb.toString();
    }

    /**
     * A string describing the bitmap.
     *
     * @return the string
     */
    @Override
    public String toString() {
        StringBuilder answer = new StringBuilder();
        IntIterator i = this.intIterator();
        answer.append("{");
        if (i.hasNext())
            answer.append(i.next());
        while (i.hasNext()) {
            answer.append(",");
            answer.append(i.next());
        }
        answer.append("}");
        return answer.toString();
    }

    /**
     * swap the content of the bitmap with another.
     *
     * @param other bitmap to swap with
     */
    public void swap(final EWAHCompressedBitmap32 other) {
        Buffer tmp = this.buffer;
        this.buffer = other.buffer;
        other.buffer = tmp;

        int tmp2 = this.rlw.position;
        this.rlw.position = other.rlw.position;
        other.rlw.position = tmp2;

        Buffer tmp3 = this.rlw.buffer;
        this.rlw.buffer = other.rlw.buffer;
        other.rlw.buffer = tmp3;

        int tmp4 = this.sizeInBits;
        this.sizeInBits = other.sizeInBits;
        other.sizeInBits = tmp4;
    }

    /**
     * Reduce the internal buffer to its minimal allowable size (given by
     * this.actualsizeinwords). This can free memory.
     */
    public void trim() {
        this.buffer.trim();
    }

    /**
     * Returns a new compressed bitmap containing the bitwise XOR values of
     * the current bitmap with some other bitmap.
     * 
     * The running time is proportional to the sum of the compressed sizes
     * (as reported by sizeInBytes()).
     * 
     * If you are not planning on adding to the resulting bitmap, you may
     * call the trim() method to reduce memory usage.
     * 
     * The current bitmap is not modified.
     *
     * @param a the other bitmap (it will not be modified)
     * @return the EWAH compressed bitmap
     */
    @Override
    public EWAHCompressedBitmap32 xor(final EWAHCompressedBitmap32 a) {
    	int size = this.buffer.sizeInWords() + a.buffer.sizeInWords();
        final EWAHCompressedBitmap32 container = new EWAHCompressedBitmap32(size);
        xorToContainer(a, container);
        return container;
    }

    /**
     * Computes a new compressed bitmap containing the bitwise XOR values of
     * the current bitmap with some other bitmap.
     * 
     * The running time is proportional to the sum of the compressed sizes
     * (as reported by sizeInBytes()).
     * 
     * The current bitmap is not modified.
     * 
     * The content of the container is overwritten.
     *
     * @param a         the other bitmap (it will not be modified)
     * @param container where we store the result
     */
    public void xorToContainer(final EWAHCompressedBitmap32 a,
                               final BitmapStorage32 container) {
        container.clear();
        final EWAHIterator32 i = a.getEWAHIterator();
        final EWAHIterator32 j = getEWAHIterator();
        final IteratingBufferedRunningLengthWord32 rlwi = new IteratingBufferedRunningLengthWord32(
                i);
        final IteratingBufferedRunningLengthWord32 rlwj = new IteratingBufferedRunningLengthWord32(
                j);
        while ((rlwi.size() > 0) && (rlwj.size() > 0)) {
            while ((rlwi.getRunningLength() > 0)
                    || (rlwj.getRunningLength() > 0)) {
                final boolean i_is_prey = rlwi
                        .getRunningLength() < rlwj
                        .getRunningLength();
                final IteratingBufferedRunningLengthWord32 prey = i_is_prey ? rlwi
                        : rlwj;
                final IteratingBufferedRunningLengthWord32 predator = i_is_prey ? rlwj
                        : rlwi;
                final int index = (!predator.getRunningBit()) ? prey.discharge(container,
                        predator.getRunningLength()) : prey.dischargeNegated(
                        container,
                        predator.getRunningLength());
                container.addStreamOfEmptyWords(predator.getRunningBit(),
                        predator.getRunningLength()
                                - index
                );
                predator.discardRunningWords();
            }
            final int nbre_literal = Math.min(
                    rlwi.getNumberOfLiteralWords(),
                    rlwj.getNumberOfLiteralWords());
            if (nbre_literal > 0) {
                for (int k = 0; k < nbre_literal; ++k)
                    container.addLiteralWord(rlwi.getLiteralWordAt(k)
                            ^ rlwj.getLiteralWordAt(k));
                rlwi.discardFirstWords(nbre_literal);
                rlwj.discardFirstWords(nbre_literal);
            }
        }
        final boolean i_remains = rlwi.size() > 0;
        final IteratingBufferedRunningLengthWord32 remaining = i_remains ? rlwi
                : rlwj;
        remaining.discharge(container);
        container.setSizeInBitsWithinLastWord(Math.max(sizeInBits(), a.sizeInBits()));
    }
    

    

    /**
     * Returns the cardinality of the result of a bitwise XOR of the values
     * of the current bitmap with some other bitmap. Avoids allocating an
     * intermediate bitmap to hold the result of the OR.
     * 
     * The current bitmap is not modified.
     *
     * @param a the other bitmap (it will not be modified)
     * @return the cardinality
     */
    public int xorCardinality(final EWAHCompressedBitmap32 a) {
        final BitCounter32 counter = new BitCounter32();
        xorToContainer(a, counter);
        return counter.getCount();
    }

    /**
     * Returns a new compressed bitmap containing the composition of
     * the current bitmap with some other bitmap.
     *
     * The composition A.compose(B) is defined as follows: we retain
     * the ith set bit of A only if the ith bit of B is set. For example, 
     * if you have the following bitmap A = { 0, 1, 0, 1, 1, 0 } and want
     * to keep only the second and third ones, you can call A.compose(B) 
     * with B = { 0, 1, 1 } and you will get C = { 0, 0, 0, 1, 1, 0 }.
     *
     *
     * If you are not planning on adding to the resulting bitmap, you may
     * call the trim() method to reduce memory usage.
     *
     * The current bitmap is not modified.
     *
     * @param a the other bitmap (it will not be modified)
     * @return the EWAH compressed bitmap
     */
    @Override
    public EWAHCompressedBitmap32 compose(EWAHCompressedBitmap32 a) {
        int size = this.buffer.sizeInWords();
        final EWAHCompressedBitmap32 container = new EWAHCompressedBitmap32(size);
        composeToContainer(a, container);
        return container;
    }

    /**
     * Computes a new compressed bitmap containing the composition of
     * the current bitmap with some other bitmap.
     *
     * The composition A.compose(B) is defined as follows: we retain
     * the ith set bit of A only if the ith bit of B is set. For example, 
     * if you have the following bitmap A = { 0, 1, 0, 1, 1, 0 } and want
     * to keep only the second and third ones, you can call A.compose(B) 
     * with B = { 0, 1, 1 } and you will get C = { 0, 0, 0, 1, 1, 0 }.
     *
     *
     * The current bitmap is not modified.
     *
     * The content of the container is overwritten.
     *
     * @param a         the other bitmap (it will not be modified)
     * @param container where we store the result
     */
    public void composeToContainer(final EWAHCompressedBitmap32 a,
                                   final EWAHCompressedBitmap32 container) {
        container.clear();
        final ChunkIterator iterator = chunkIterator();
        final ChunkIterator aIterator = a.chunkIterator();
        int index = 0;
        while(iterator.hasNext() && aIterator.hasNext()) {
            if(!iterator.nextBit()) {
                int length = iterator.nextLength();
                index += length;
                container.setSizeInBits(index, false);
                iterator.move(length);
            } else {
                int length = Math.min(iterator.nextLength(), aIterator.nextLength());
                index += length;
                container.setSizeInBits(index, aIterator.nextBit());
                iterator.move(length);
                aIterator.move(length);
            }
        }
        container.setSizeInBits(sizeInBits, false);
    }

    /**
     * For internal use. Computes the bitwise and of the provided bitmaps
     * and stores the result in the container.
     * 
     * The content of the container is overwritten.
     *
     * @param container where the result is stored
     * @param bitmaps   bitmaps to AND
     */
    public static void andWithContainer(final BitmapStorage32 container,
                                        final EWAHCompressedBitmap32... bitmaps) {
        if (bitmaps.length == 1)
            throw new IllegalArgumentException(
                    "Need at least one bitmap");
        if (bitmaps.length == 2) {
            bitmaps[0].andToContainer(bitmaps[1], container);
            return;
        }
        int initialSize = calculateInitialSize(bitmaps);
        EWAHCompressedBitmap32 answer = new EWAHCompressedBitmap32(initialSize);
        EWAHCompressedBitmap32 tmp = new EWAHCompressedBitmap32(initialSize);
        bitmaps[0].andToContainer(bitmaps[1], answer);
        for (int k = 2; k < bitmaps.length - 1; ++k) {
            answer.andToContainer(bitmaps[k], tmp);
            tmp.swap(answer);
            tmp.clear();
        }
        answer.andToContainer(bitmaps[bitmaps.length - 1], container);
    }
    
    private static int calculateInitialSize(final EWAHCompressedBitmap32... bitmaps) {
        int initialSize = 0;
        for (EWAHCompressedBitmap32 bitmap : bitmaps)
            initialSize = Math.max(bitmap.buffer.sizeInWords(), initialSize);
        return initialSize;
    }

    /**
     * Returns a new compressed bitmap containing the bitwise AND values of
     * the provided bitmaps.
     * 
     * It may or may not be faster than doing the aggregation two-by-two
     * (A.and(B).and(C)).
     * 
     * If only one bitmap is provided, it is returned as is.
     * 
     * If you are not planning on adding to the resulting bitmap, you may
     * call the trim() method to reduce memory usage.
     *
     * @param bitmaps bitmaps to AND together
     * @return result of the AND
     */
    public static EWAHCompressedBitmap32 and(
            final EWAHCompressedBitmap32... bitmaps) {
        if (bitmaps.length == 1)
            return bitmaps[0];
        if (bitmaps.length == 2)
            return bitmaps[0].and(bitmaps[1]);
        int initialSize = calculateInitialSize(bitmaps);
        EWAHCompressedBitmap32 answer = new EWAHCompressedBitmap32(initialSize);
        EWAHCompressedBitmap32 tmp = new EWAHCompressedBitmap32(initialSize);
        bitmaps[0].andToContainer(bitmaps[1], answer);
        for (int k = 2; k < bitmaps.length; ++k) {
            answer.andToContainer(bitmaps[k], tmp);
            tmp.swap(answer);
            tmp.clear();
        }
        return answer;
    }

    /**
     * Returns the cardinality of the result of a bitwise AND of the values
     * of the provided bitmaps. Avoids allocating an intermediate
     * bitmap to hold the result of the AND.
     *
     * @param bitmaps bitmaps to AND
     * @return the cardinality
     */
    public static int andCardinality(
            final EWAHCompressedBitmap32... bitmaps) {
        if (bitmaps.length == 1)
            return bitmaps[0].cardinality();
        final BitCounter32 counter = new BitCounter32();
        andWithContainer(counter, bitmaps);
        return counter.getCount();
    }

    /**
     * Return a bitmap with the bit set to true at the given positions. The
     * positions should be given in sorted order.
     * 
     * (This is a convenience method.)
     *
     * @param setbits list of set bit positions
     * @return the bitmap
     * @since 0.4.5
     */
    public static EWAHCompressedBitmap32 bitmapOf(int... setbits) {
        EWAHCompressedBitmap32 a = new EWAHCompressedBitmap32();
        for (int k : setbits)
            a.set(k);
        return a;
    }

    /**
     * For internal use. Computes the bitwise or of the provided bitmaps and
     * stores the result in the container.
     * 
     * The content of the container is overwritten.
     *
     * @param container where store the result
     * @param bitmaps   to be aggregated
     */
    public static void orWithContainer(final BitmapStorage32 container,
                                       final EWAHCompressedBitmap32... bitmaps) {
        if (bitmaps.length < 2)
            throw new IllegalArgumentException(
                    "You should provide at least two bitmaps, provided "
                            + bitmaps.length
            );
        int size = 0;
        int sinbits = 0;
        for (EWAHCompressedBitmap32 b : bitmaps) {
            size += b.sizeInBytes();
            if (sinbits < b.sizeInBits())
                sinbits = b.sizeInBits();
        }
        if (size * 8 > sinbits) {
            FastAggregation32.bufferedorWithContainer(container,
                    65536, bitmaps);
        } else {
            FastAggregation32.orToContainer(container, bitmaps);
        }
    }

    /**
     * For internal use. Computes the bitwise xor of the provided bitmaps
     * and stores the result in the container.
     * 
     * The content of the container is overwritten.
     *
     * @param container where store the result
     * @param bitmaps   to be aggregated
     */
    public static void xorWithContainer(final BitmapStorage32 container,
                                        final EWAHCompressedBitmap32... bitmaps) {
        if (bitmaps.length < 2)
            throw new IllegalArgumentException(
                    "You should provide at least two bitmaps, provided "
                            + bitmaps.length
            );
        int size = 0;
        int sinbits = 0;
        for (EWAHCompressedBitmap32 b : bitmaps) {
            size += b.sizeInBytes();
            if (sinbits < b.sizeInBits())
                sinbits = b.sizeInBits();
        }
        if (size * 8 > sinbits) {
            FastAggregation32.bufferedxorWithContainer(container,
                    65536, bitmaps);
        } else {
            FastAggregation32.xorToContainer(container, bitmaps);
        }
    }

    /**
     * Returns a new compressed bitmap containing the bitwise OR values of
     * the provided bitmaps. This is typically faster than doing the
     * aggregation two-by-two (A.or(B).or(C).or(D)).
     * 
     * If only one bitmap is provided, it is returned as is.
     * 
     * If you are not planning on adding to the resulting bitmap, you may
     * call the trim() method to reduce memory usage.
     *
     * @param bitmaps bitmaps to OR together
     * @return result of the OR
     */
    public static EWAHCompressedBitmap32 or(
            final EWAHCompressedBitmap32... bitmaps) {
        if (bitmaps.length == 1)
            return bitmaps[0];
        int largestSize = calculateInitialSize(bitmaps);
        final EWAHCompressedBitmap32 container = new EWAHCompressedBitmap32((int) (largestSize * 1.5));
        orWithContainer(container, bitmaps);
        return container;
    }

    /**
     * Returns a new compressed bitmap containing the bitwise XOR values of
     * the provided bitmaps. This is typically faster than doing the
     * aggregation two-by-two (A.xor(B).xor(C).xor(D)).
     * 
     * If only one bitmap is provided, it is returned as is.
     * 
     * If you are not planning on adding to the resulting bitmap, you may
     * call the trim() method to reduce memory usage.
     *
     * @param bitmaps bitmaps to XOR together
     * @return result of the XOR
     */
    public static EWAHCompressedBitmap32 xor(
            final EWAHCompressedBitmap32... bitmaps) {
        if (bitmaps.length == 1)
            return bitmaps[0];
        int largestSize = calculateInitialSize(bitmaps);
        final EWAHCompressedBitmap32 container = new EWAHCompressedBitmap32((int) (largestSize * 1.5));
        xorWithContainer(container, bitmaps);
        return container;
    }

    /**
     * Returns the cardinality of the result of a bitwise OR of the values
     * of the provided bitmaps. Avoids allocating an intermediate
     * bitmap to hold the result of the OR.
     *
     * @param bitmaps bitmaps to OR
     * @return the cardinality
     */
    public static int orCardinality(final EWAHCompressedBitmap32... bitmaps) {
        if (bitmaps.length == 1)
            return bitmaps[0].cardinality();
        final BitCounter32 counter = new BitCounter32();
        orWithContainer(counter, bitmaps);
        return counter.getCount();
    }

    /**
     * The buffer
     */
    protected Buffer buffer = null;

    /**
     * The current (last) running length word.
     */
    private RunningLengthWord32 rlw = null;

    /**
     * sizeInBits: number of bits in the (uncompressed) bitmap.
     */
    private int sizeInBits = 0;

    /**
     * whether we adjust after some aggregation by adding in zeroes *
     */
    public static final boolean ADJUST_CONTAINER_SIZE_WHEN_AGGREGATING = true;

    /**
     * The Constant WORD_IN_BITS represents the number of bits in a int.
     */
    public static final int WORD_IN_BITS = 32;

    static final long serialVersionUID = 1L;
}
