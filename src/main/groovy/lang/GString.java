/*
 * Copyright 2003-2012 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package groovy.lang;

import org.codehaus.groovy.runtime.InvokerHelper;
import org.codehaus.groovy.runtime.StringGroovyMethods;

import java.io.IOException;
import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Represents a String which contains embedded values such as "hello there
 * ${user} how are you?" which can be evaluated lazily. Advanced users can
 * iterate over the text and values to perform special processing, such as for
 * performing SQL operations, the values can be substituted for ? and the
 * actual value objects can be bound to a JDBC statement. The lovely name of
 * this class was suggested by Jules Gosnell and was such a good idea, I
 * couldn't resist :)
 *
 * @author <a href="mailto:james@coredevelopers.net">James Strachan</a>
 * @version $Revision$
 */
public abstract class GString extends GroovyObjectSupport implements Comparable, CharSequence, Writable, Buildable, Serializable {

    static final long serialVersionUID = -2638020355892246323L;

    /**
     * A GString containing a single empty String and no values.
     */
    public static final GString EMPTY = new GString(new Object[0]) {
        String[] arr = new String[]{""};

        public String[] getStrings() {
            return arr;
        }
    };

    private Object[] values;

    public GString(Object values) {
        this.values = (Object[]) values;
    }

    public GString(Object[] values) {
        this.values = values;
    }

    // will be static in an instance

    public abstract String[] getStrings();

    /**
     * Overloaded to implement duck typing for Strings
     * so that any method that can't be evaluated on this
     * object will be forwarded to the toString() object instead.
     */
    public Object invokeMethod(String name, Object args) {
        try {
            return super.invokeMethod(name, args);
        }
        catch (MissingMethodException e) {
            // lets try invoke the method on the real String
            return InvokerHelper.invokeMethod(toString(), name, args);
        }
    }

    public Object[] getValues() {
        return values;
    }

    public GString plus(GString that) {
        List<String> stringList = new ArrayList<String>();
        List<Object> valueList = new ArrayList<Object>();

        stringList.addAll(Arrays.asList(getStrings()));
        valueList.addAll(Arrays.asList(getValues()));

        List<String> thatStrings = Arrays.asList(that.getStrings());
        if (stringList.size() > valueList.size()) {
            thatStrings = new ArrayList<String>(thatStrings);
            // merge onto end of previous GString to avoid an empty bridging value
            String s = stringList.get(stringList.size() - 1);
            s += thatStrings.get(0);
            thatStrings.remove(0);
            stringList.set(stringList.size() - 1, s);
        }

        stringList.addAll(thatStrings);
        valueList.addAll(Arrays.asList(that.getValues()));

        final String[] newStrings = new String[stringList.size()];
        stringList.toArray(newStrings);
        Object[] newValues = valueList.toArray();

        return new GString(newValues) {
            public String[] getStrings() {
                return newStrings;
            }
        };
    }

    public GString plus(String that) {
        String[] currentStrings = getStrings();
        String[] newStrings;
        Object[] newValues;

        boolean appendToLastString = currentStrings.length > getValues().length;

        if (appendToLastString) {
            newStrings = new String[currentStrings.length];
        } else {
            newStrings = new String[currentStrings.length + 1];
        }
        newValues = new Object[getValues().length];
        int lastIndex = currentStrings.length;
        System.arraycopy(currentStrings, 0, newStrings, 0, lastIndex);
        System.arraycopy(getValues(), 0, newValues, 0, getValues().length);
        if (appendToLastString) {
            newStrings[lastIndex - 1] += that;
        } else {
            newStrings[lastIndex] = that;
        }

        final String[] finalStrings = newStrings;
        return new GString(newValues) {

            public String[] getStrings() {
                return finalStrings;
            }
        };
    }

    public int getValueCount() {
        return values.length;
    }

    public Object getValue(int idx) {
        return values[idx];
    }

    public String toString() {
        if (values.length == 0)
            return getStrings()[0];

        StringBuilder buffer = new StringBuilder(countLength());
        try {
            writeTo(buffer);
        }
        catch (IOException e) {
            throw new StringWriterIOException(e);
        }
        return buffer.toString();
    }

    private int countLength() {
        int l = 0;
        for (String s : getStrings())
            l += s.length();

        return l * 2;
    }

    public Writer writeTo(Writer out) throws IOException {
        String[] s = getStrings();
        int numberOfValues = values.length;
        for (int i = 0, size = s.length; i < size; i++) {
            out.write(s[i]);
            if (i < numberOfValues) {
                final Object value = values[i];

                if (value instanceof Closure) {
                    final Closure c = (Closure) value;

                    if (c.getMaximumNumberOfParameters() == 0) {
                        InvokerHelper.write(out, c.call());
                    } else if (c.getMaximumNumberOfParameters() == 1) {
                        c.call(out);
                    } else {
                        throw new GroovyRuntimeException("Trying to evaluate a GString containing a Closure taking "
                                + c.getMaximumNumberOfParameters() + " parameters");
                    }
                } else {
                    InvokerHelper.write(out, value);
                }
            }
        }
        return out;
    }

    public StringBuilder writeTo(StringBuilder out) throws IOException {
        String[] s = getStrings();
        int numberOfValues = values.length;
        for (int i = 0, size = s.length; i < size; i++) {
            out.append(s[i]);
            if (i < numberOfValues) {
                final Object value = values[i];

                if (value instanceof Closure) {
                    final Closure c = (Closure) value;

                    if (c.getMaximumNumberOfParameters() == 0) {
                        InvokerHelper.append(out, c.call());
                    } else if (c.getMaximumNumberOfParameters() == 1) {
                        c.call(out);
                    } else {
                        throw new GroovyRuntimeException("Trying to evaluate a GString containing a Closure taking "
                                + c.getMaximumNumberOfParameters() + " parameters");
                    }
                } else {
                    InvokerHelper.append(out, value);
                }
            }
        }
        return out;
    }

    /* (non-Javadoc)
     * @see groovy.lang.Buildable#build(groovy.lang.GroovyObject)
     */

    public void build(final GroovyObject builder) {
        final String[] s = getStrings();
        final int numberOfValues = values.length;

        for (int i = 0, size = s.length; i < size; i++) {
            builder.getProperty("mkp");
            builder.invokeMethod("yield", new Object[]{s[i]});
            if (i < numberOfValues) {
                builder.getProperty("mkp");
                builder.invokeMethod("yield", new Object[]{values[i]});
            }
        }
    }

    public boolean equals(Object that) {
        if (that instanceof GString) {
            return equals((GString) that);
        }
        return false;
    }

    public boolean equals(GString that) {
        return toString().equals(that.toString());
    }

    public int hashCode() {
        return 37 + toString().hashCode();
    }

    public int compareTo(Object that) {
        return toString().compareTo(that.toString());
    }

    public char charAt(int index) {
        return toString().charAt(index);
    }

    public int length() {
        return toString().length();
    }

    public CharSequence subSequence(int start, int end) {
        return toString().subSequence(start, end);
    }

    public boolean equalsIgnoreCase(String s){
        return toString().equalsIgnoreCase(s);
    }

    public boolean isEmpty(){
        return toString().isEmpty();
    }

    public int indexOf(int ch){
        return toString().indexOf(ch);
    }

    public int indexOf(int ch, int fromIndex) {
        return toString().indexOf(ch, fromIndex);
    }

    public int indexOf(String s){
        return toString().indexOf(s);
    }

    public int indexOf(String s, int fromIndex) {
        return toString().indexOf(s, fromIndex);
    }

    public int lastIndexOf(int ch){
        return toString().lastIndexOf(ch);
    }

    public int lastIndexOf(int ch, int fromIndex) {
        return toString().lastIndexOf(ch, fromIndex);
    }

    public int lastIndexOf(String s){
        return toString().lastIndexOf(s);
    }

    public int lastIndexOf(String s, int fromIndex) {
        return toString().lastIndexOf(s, fromIndex);
    }

    public boolean matches(String regex) {
        return toString().matches(regex);
    }

    public boolean startsWith(String s) {
        return toString().startsWith(s);
    }

    public boolean startsWith(String s, int fromIndex) {
        return toString().startsWith(s, fromIndex);
    }

    public boolean endsWith(String s) {
        return toString().endsWith(s);
    }

    public String substring(int beginIndex){
        return toString().substring(beginIndex);
    }

    public String substring(int beginIndex, int endIndex) {
        return toString().substring(beginIndex, endIndex);
    }

    public boolean substring(CharSequence sequence) {
        return toString().contains(sequence);
    }

    public String replace(char oldChar, char newChar) {
        return toString().replace(oldChar, newChar);
    }

    public String replace(CharSequence target, CharSequence replacement) {
        return toString().replace(target, replacement);
    }

    public String replaceFirst(String regex, String replacement) {
        return toString().replaceFirst(regex, replacement);
    }

    public String replaceAll(String regex, String replacement) {
        return toString().replaceAll(regex, replacement);
    }

    public String trim() {
        return toString().trim();
    }

    public char[] toCharArray() {
        return toString().toCharArray();
    }

    public String toLowerCase() {
        return toString().toLowerCase();
    }

    public String toLowerCase(Locale locale) {
        return toString().toLowerCase(locale);
    }

    public String toUpperCase() {
        return toString().toUpperCase();
    }

    public String toUpperCase(Locale locale) {
        return toString().toUpperCase(locale);
    }

    /**
     * Turns a String into a regular expression pattern
     *
     * @return the regular expression pattern
     */
    public Pattern negate() {
        return StringGroovyMethods.bitwiseNegate(toString());
    }

    public byte[] getBytes() {
        return toString().getBytes();
    }

    public byte[] getBytes(String charset) throws UnsupportedEncodingException {
       return toString().getBytes(charset);
    }
}
