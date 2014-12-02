package com.googlecode.javaewah32;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Arrays;

import com.googlecode.javaewah32.Buffer;

class IntArray implements Buffer, Externalizable, Cloneable {

	public IntArray() {
        this(DEFAULT_BUFFER_SIZE);
    }
	
	public IntArray(int bufferSize) {
        if(bufferSize < 1) {
        	bufferSize = 1;
        }
        this.buffer = new int[bufferSize];
    }
	
	@Override
	public int sizeInWords() {
		return this.actualSizeInWords;
	}
	
	@Override
	public int getWord(int position) {
		return this.buffer[position];
	}
	
	@Override
	public int getLastWord() {
		return getWord(this.actualSizeInWords - 1);
	}
	
	@Override
	public int[] getWords() {
		if(this.actualSizeInWords < this.buffer.length) {
			return Arrays.copyOf(this.buffer, this.actualSizeInWords);			
		}
		return this.buffer;
	}
	
	@Override
	public void clear() {
		this.actualSizeInWords = 1;
        // buffer is not fully cleared but any new set operations should
        // overwrite
        // stale data
        this.buffer[0] = 0;
	}
	
	@Override
    public void trim() {
        this.buffer = Arrays.copyOf(this.buffer, this.actualSizeInWords);
    }
    
	@Override
	public void setWord(int position, int word) {
		this.buffer[position] = word;
	}
	
	@Override
	public void setLastWord(int word) {
		setWord(this.actualSizeInWords - 1, word);
	}
	
	@Override
	public void push_back(int data) {
        int size = newSizeInWords(1);
        if (size >= this.buffer.length) {
            int oldBuffer[] = this.buffer;
            this.buffer = new int[size];
            System.arraycopy(oldBuffer, 0, this.buffer, 0, oldBuffer.length);
        }
        this.buffer[this.actualSizeInWords++] = data;
    }

	@Override
    public void push_back(int[] data, int start, int number) {
        int size = newSizeInWords(number);
        if (size >= this.buffer.length) {
            int oldBuffer[] = this.buffer;
            this.buffer = new int[size];
            System.arraycopy(oldBuffer, 0, this.buffer, 0, oldBuffer.length);
        }
        System.arraycopy(data, start, this.buffer, this.actualSizeInWords, number);
        this.actualSizeInWords += number;
    }
    
	@Override
    public void negative_push_back(int[] data, int start, int number) {
        int size = newSizeInWords(number);
        if (size >= this.buffer.length) {
            int oldBuffer[] = this.buffer;
            this.buffer = new int[size];
            System.arraycopy(oldBuffer, 0, this.buffer, 0, oldBuffer.length);
        }
        for (int i = 0; i < number; ++i) {
            this.buffer[this.actualSizeInWords + i] = ~data[start + i];
        }
        this.actualSizeInWords += number;
    }
	
	@Override
	public void removeLastWord() {
		setWord(--this.actualSizeInWords, 0);
	}
	
	@Override
	public void negateWord(int position) {
		this.buffer[position] = ~this.buffer[position];
	}
	
	@Override
	public void andWord(int position, int mask) {
		this.buffer[position] &= mask;
	}
	
	@Override
	public void orWord(int position, int mask) {
		this.buffer[position] |= mask;
	}
	
	@Override
	public void andLastWord(int mask) {
		andWord(this.actualSizeInWords - 1, mask);
	}
	
	@Override
	public void orLastWord(int mask) {
		orWord(this.actualSizeInWords - 1, mask);
	}
	
	@Override
	public void expand(int position, int length) {
		int size = newSizeInWords(length);
        int oldBuffer[] = this.buffer;
        if(size >= this.buffer.length) {
            this.buffer = new int[size];
            System.arraycopy(oldBuffer, 0, this.buffer, 0, position);
        }
        System.arraycopy(oldBuffer, position, this.buffer, position + length, this.actualSizeInWords - position);
        this.actualSizeInWords += length;
	}
	
	@Override
	public void collapse(int position, int length) {
		System.arraycopy(this.buffer, position + length, this.buffer, position, this.actualSizeInWords - position - length);
		for(int i = 0; i < length; ++i) {
			removeLastWord();
		}
	}
	
	@Override
	public Buffer clone() {
		IntArray clone = null;
		try {
			clone = (IntArray) super.clone();
			clone.buffer = this.buffer.clone();
			clone.actualSizeInWords = this.actualSizeInWords;
		} catch (CloneNotSupportedException e) {
			e.printStackTrace(); // cannot happen
		}
		return clone;
	}
	
	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
        out.writeInt(this.actualSizeInWords);
        for (int i = 0; i < this.actualSizeInWords; ++i) {
            out.writeInt(this.buffer[i]);
        }
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		this.actualSizeInWords = in.readInt();
        if (this.buffer.length < this.actualSizeInWords) {
            this.buffer = new int[this.actualSizeInWords];
        }
        for (int i = 0; i < this.actualSizeInWords; ++i) {
            this.buffer[i] = in.readInt();
        }
	}
	
	/**
     * For internal use.
     *
     * @param number the number of words to add
     */
    private int newSizeInWords(int number) {
    	int size = this.actualSizeInWords + number;
        if (size >= this.buffer.length) {
            if (size < 32768)
                size = size * 2;
            else if (size * 3 / 2 < size) // overflow
                size = Integer.MAX_VALUE;
            else
                size = size * 3 / 2;
        }
        return size;
    }
	
    /**
     * The actual size in words.
     */
    private int actualSizeInWords = 1;
	
    /**
     * The buffer (array of 32-bit words)
     */
    private int buffer[] = null;
    
    /**
     * The Constant DEFAULT_BUFFER_SIZE: default memory allocation when the
     * object is constructed.
     */
    private static final int DEFAULT_BUFFER_SIZE = 4;
	
}
