/*
 * Copyright (c) 1997, 2013, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package java.lang;
import java.lang.ref.*;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * This class provides thread-local variables.  These variables differ from
 * their normal counterparts in that each thread that accesses one (via its
 * {@code get} or {@code set} method) has its own, independently initialized
 * copy of the variable.  {@code ThreadLocal} instances are typically private
 * static fields in classes that wish to associate state with a thread (e.g.,
 * a user ID or Transaction ID).
 *
 * <p>For example, the class below generates unique identifiers local to each
 * thread.
 * A thread's id is assigned the first time it invokes {@code ThreadId.get()}
 * and remains unchanged on subsequent calls.
 * <pre>
 * import java.util.concurrent.atomic.AtomicInteger;
 *
 * public class ThreadId {
 *     // Atomic integer containing the next thread ID to be assigned
 *     private static final AtomicInteger nextId = new AtomicInteger(0);
 *
 *     // Thread local variable containing each thread's ID
 *     private static final ThreadLocal&lt;Integer&gt; threadId =
 *         new ThreadLocal&lt;Integer&gt;() {
 *             &#64;Override protected Integer initialValue() {
 *                 return nextId.getAndIncrement();
 *         }
 *     };
 *
 *     // Returns the current thread's unique ID, assigning it if necessary
 *     public static int get() {
 *         return threadId.get();
 *     }
 * }
 * </pre>
 * <p>Each thread holds an implicit reference to its copy of a thread-local
 * variable as long as the thread is alive and the {@code ThreadLocal}
 * instance is accessible; after a thread goes away, all of its copies of
 * thread-local instances are subject to garbage collection (unless other
 * references to these copies exist).
 *
 * @author  Josh Bloch and Doug Lea
 * @since   1.2
 */
public class ThreadLocal<T> {
    /**
     * ThreadLocals rely on per-thread linear-probe hash maps attached
     * to each thread (Thread.threadLocals and
     * inheritableThreadLocals).  The ThreadLocal objects act as keys,
     * searched via threadLocalHashCode.  This is a custom hash code
     * (useful only within ThreadLocalMaps) that eliminates collisions
     * in the common case where consecutively constructed ThreadLocals
     * are used by the same threads, while remaining well-behaved in
     * less common cases.
     */
    // 分配的hash code
    private final int threadLocalHashCode = nextHashCode();

    /**
     * The next hash code to be given out. Updated atomically. Starts at
     * zero.
     */
    // 原子整形类型，从0开始
    private static AtomicInteger nextHashCode =
        new AtomicInteger();

    /**
     * The difference between successively generated hash codes - turns
     * implicit sequential thread-local IDs into near-optimally spread
     * multiplicative hash values for power-of-two-sized tables.
     */
    // hash code 每次增加1640531527
    private static final int HASH_INCREMENT = 0x61c88647;

    /**
     * Returns the next hash code.
     */
    // 获取下一个hash值，从0开始， 每次增加hash_increment
    private static int nextHashCode() {
        return nextHashCode.getAndAdd(HASH_INCREMENT);
    }

    /**
     * Returns the current thread's "initial value" for this
     * thread-local variable.  This method will be invoked the first
     * time a thread accesses the variable with the {@link #get}
     * method, unless the thread previously invoked the {@link #set}
     * method, in which case the {@code initialValue} method will not
     * be invoked for the thread.  Normally, this method is invoked at
     * most once per thread, but it may be invoked again in case of
     * subsequent invocations of {@link #remove} followed by {@link #get}.
     *
     * <p>This implementation simply returns {@code null}; if the
     * programmer desires thread-local variables to have an initial
     * value other than {@code null}, {@code ThreadLocal} must be
     * subclassed, and this method overridden.  Typically, an
     * anonymous inner class will be used.
     *
     * @return the initial value for this thread-local
     */
    protected T initialValue() {
        return null;
    }

    /**
     * Creates a thread local variable. The initial value of the variable is
     * determined by invoking the {@code get} method on the {@code Supplier}.
     *
     * @param <S> the type of the thread local's value
     * @param supplier the supplier to be used to determine the initial value
     * @return a new thread local variable
     * @throws NullPointerException if the specified supplier is null
     * @since 1.8
     */
    public static <S> ThreadLocal<S> withInitial(Supplier<? extends S> supplier) {
        return new SuppliedThreadLocal<>(supplier);
    }

    /**
     * Creates a thread local variable.
     * @see #withInitial(java.util.function.Supplier)
     */
    public ThreadLocal() {
    }

    /**
     * Returns the value in the current thread's copy of this
     * thread-local variable.  If the variable has no value for the
     * current thread, it is first initialized to the value returned
     * by an invocation of the {@link #initialValue} method.
     *
     * @return the current thread's value of this thread-local
     */
    public T get() {
        Thread t = Thread.currentThread();
        ThreadLocalMap map = getMap(t);
        if (map != null) {
            //调用getEntry方法，通过调用get()方法的ThreadLocal获取对应的entry
            ThreadLocalMap.Entry e = map.getEntry(this);
            //Entry不为空则代表找到目标的Entry，返回该Entry的Value值
            if (e != null) {
                @SuppressWarnings("unchecked")
                T result = (T)e.value;
                return result;
            }
        }
        //该线程的ThreadLocalMap为空则初始化一个
        return setInitialValue();
    }

    /**
     * Variant of set() to establish initialValue. Used instead
     * of set() in case user has overridden the set() method.
     *
     * @return the initial value
     */
    private T setInitialValue() {
        //默认null，需要用户自己重写该方法
        T value = initialValue();
        //当前线程
        Thread t = Thread.currentThread();
        //拿到当前线程的threadLocals
        ThreadLocalMap map = getMap(t);
        //threadLocals不为空则将当前的ThreadLocal作为key，null作为value，插入到ThreadLocalMap中
        if (map != null)
            map.set(this, value);
        else
        //threadLocals为空则调用创建一个ThreadLocalMap，并新建一个Entry放入该ThreadLocalMap中
        //调用set方法的ThreadLocal和value作为该Entry的key和value
            createMap(t, value);
        return value;
    }

    /**
     * Sets the current thread's copy of this thread-local variable
     * to the specified value.  Most subclasses will have no need to
     * override this method, relying solely on the {@link #initialValue}
     * method to set the values of thread-locals.
     *
     * @param value the value to be stored in the current thread's copy of
     *        this thread-local.
     */
    public void set(T value) {
        Thread t = Thread.currentThread();
        //获取当前线程的ThreadLocalMap
        ThreadLocalMap map = getMap(t);
        //当前线程的ThreadLocalMap不为空则调用set方法，this为调用该方法的ThreadLocal对象
        if (map != null)
            map.set(this, value);
        //map为空则调用createMap方法创建一个新的ThreadLocalMap，并新建一个Entry放入该
        //ThreadLocalMap,调用set方法的ThreadLocal和传入的Value作为该Entry的key和value
        else
            createMap(t, value);
    }

    /**
     * Removes the current thread's value for this thread-local
     * variable.  If this thread-local variable is subsequently
     * {@linkplain #get read} by the current thread, its value will be
     * reinitialized by invoking its {@link #initialValue} method,
     * unless its value is {@linkplain #set set} by the current thread
     * in the interim.  This may result in multiple invocations of the
     * {@code initialValue} method in the current thread.
     *
     * @since 1.5
     */
     public void remove() {
         //获取当前线程的ThreadLocalMap
         ThreadLocalMap m = getMap(Thread.currentThread());
         if (m != null)
             m.remove(this);//调用此方法的ThreadLocal作为入参，调用remove方法
     }

    /**
     * Get the map associated with a ThreadLocal. Overridden in
     * InheritableThreadLocal.
     *
     * @param  t the current thread
     * @return the map
     */
    //返回线程t的threadLocals属性
    ThreadLocalMap getMap(Thread t) {
        return t.threadLocals;
    }

    /**
     * Create the map associated with a ThreadLocal. Overridden in
     * InheritableThreadLocal.
     *
     * @param t the current thread
     * @param firstValue value for the initial entry of the map
     */
    void createMap(Thread t, T firstValue) {
        t.threadLocals = new ThreadLocalMap(this, firstValue);
    }

    /**
     * Factory method to create map of inherited thread locals.
     * Designed to be called only from Thread constructor.
     *
     * @param  parentMap the map associated with parent thread
     * @return a map containing the parent's inheritable bindings
     */
    static ThreadLocalMap createInheritedMap(ThreadLocalMap parentMap) {
        return new ThreadLocalMap(parentMap);
    }

    /**
     * Method childValue is visibly defined in subclass
     * InheritableThreadLocal, but is internally defined here for the
     * sake of providing createInheritedMap factory method without
     * needing to subclass the map class in InheritableThreadLocal.
     * This technique is preferable to the alternative of embedding
     * instanceof tests in methods.
     */
    T childValue(T parentValue) {
        throw new UnsupportedOperationException();
    }

    /**
     * An extension of ThreadLocal that obtains its initial value from
     * the specified {@code Supplier}.
     */
    static final class SuppliedThreadLocal<T> extends ThreadLocal<T> {

        private final Supplier<? extends T> supplier;

        SuppliedThreadLocal(Supplier<? extends T> supplier) {
            this.supplier = Objects.requireNonNull(supplier);
        }

        @Override
        protected T initialValue() {
            return supplier.get();
        }
    }

    /**
     * ThreadLocalMap is a customized hash map suitable only for
     * maintaining thread local values. No operations are exported
     * outside of the ThreadLocal class. The class is package private to
     * allow declaration of fields in class Thread.  To help deal with
     * very large and long-lived usages, the hash table entries use
     * WeakReferences for keys. However, since reference queues are not
     * used, stale entries are guaranteed to be removed only when
     * the table starts running out of space.
     */
    static class ThreadLocalMap {

        /**
         * The entries in this hash map extend WeakReference, using
         * its main ref field as the key (which is always a
         * ThreadLocal object).  Note that null keys (i.e. entry.get()
         * == null) mean that the key is no longer referenced, so the
         * entry can be expunged from table.  Such entries are referred to
         * as "stale entries" in the code that follows.
         */
        //虚引用类型，Entry中的key(ThreadLocal对象)为虚引用类型，每次GC时会被回收
        static class Entry extends WeakReference<ThreadLocal<?>> {
            /** The value associated with this ThreadLocal. */
            //key为虚引用
            //值
            Object value;

            Entry(ThreadLocal<?> k, Object v) {
                super(k);
                value = v;
            }
        }

        /**
         * The initial capacity -- MUST be a power of two.
         */
        //初始化容量
        private static final int INITIAL_CAPACITY = 16;

        /**
         * The table, resized as necessary.
         * table.length MUST always be a power of two.
         */
        //结构是数组
        private Entry[] table;

        /**
         * The number of entries in the table.
         */
        //ThreadLocalMap中数据数量
        private int size = 0;

        /**
         * The next size value at which to resize.
         */
        //扩容阈值
        private int threshold; // Default to 0

        /**
         * Set the resize threshold to maintain at worst a 2/3 load factor.
         */
        private void setThreshold(int len) {
            threshold = len * 2 / 3;
        }

        /**
         * Increment i modulo len.
         */
        private static int nextIndex(int i, int len) {
            return ((i + 1 < len) ? i + 1 : 0);
        }

        /**
         * Decrement i modulo len.
         */
        private static int prevIndex(int i, int len) {
            return ((i - 1 >= 0) ? i - 1 : len - 1);
        }

        /**
         * Construct a new map initially containing (firstKey, firstValue).
         * ThreadLocalMaps are constructed lazily, so we only create
         * one when we have at least one entry to put in it.
         */
        ThreadLocalMap(ThreadLocal<?> firstKey, Object firstValue) {
            table = new Entry[INITIAL_CAPACITY];
            int i = firstKey.threadLocalHashCode & (INITIAL_CAPACITY - 1);
            table[i] = new Entry(firstKey, firstValue);
            size = 1;
            setThreshold(INITIAL_CAPACITY);
        }

        /**
         * Construct a new map including all Inheritable ThreadLocals
         * from given parent map. Called only by createInheritedMap.
         *
         * @param parentMap the map associated with parent thread.
         */
        private ThreadLocalMap(ThreadLocalMap parentMap) {
            Entry[] parentTable = parentMap.table;
            int len = parentTable.length;
            setThreshold(len);
            table = new Entry[len];

            for (int j = 0; j < len; j++) {
                Entry e = parentTable[j];
                if (e != null) {
                    @SuppressWarnings("unchecked")
                    ThreadLocal<Object> key = (ThreadLocal<Object>) e.get();
                    if (key != null) {
                        Object value = key.childValue(e.value);
                        Entry c = new Entry(key, value);
                        int h = key.threadLocalHashCode & (len - 1);
                        while (table[h] != null)
                            h = nextIndex(h, len);
                        table[h] = c;
                        size++;
                    }
                }
            }
        }

        /**
         * Get the entry associated with key.  This method
         * itself handles only the fast path: a direct hit of existing
         * key. It otherwise relays to getEntryAfterMiss.  This is
         * designed to maximize performance for direct hits, in part
         * by making this method readily inlinable.
         *
         * @param  key the thread local object
         * @return the entry associated with key, or null if no such
         */
        private Entry getEntry(ThreadLocal<?> key) {
            //根据hash code计算出索引的位置
            int i = key.threadLocalHashCode & (table.length - 1);
            Entry e = table[i];
            //如果该Entry的key和传入的key相等，则为目标entry，直接返回
            if (e != null && e.get() == key)
                return e;
            else
            //否则, e不是目标Entry，则从e之后继续寻找目标Entry
                return getEntryAfterMiss(key, i, e);
        }

        /**
         * Version of getEntry method for use when key is not found in
         * its direct hash slot.
         *
         * @param  key the thread local object
         * @param  i the table index for key's hash code
         * @param  e the entry at table[i]
         * @return the entry associated with key, or null if no such
         */
        private Entry getEntryAfterMiss(ThreadLocal<?> key, int i, Entry e) {
            Entry[] tab = table;
            int len = tab.length;

            while (e != null) {
                ThreadLocal<?> k = e.get();
                //找到目标Entry，直接返回
                if (k == key)
                    return e;
                //调用expungeStaleEntry清除key为null的元素
                if (k == null)
                    expungeStaleEntry(i);
                else
                    //下个索引位置
                    i = nextIndex(i, len);
                //下一个遍历的Entry
                e = tab[i];
            }
            //找不到返回为空
            return null;
        }

        /**
         * Set the value associated with key.
         *
         * @param key the thread local object
         * @param value the value to be set
         */
        //hashMap是用拉链法
        //ThreadLocalMap使用开放寻址法
        private void set(ThreadLocal<?> key, Object value) {

            // We don't use a fast path as with get() because it is at
            // least as common to use set() to create new entries as
            // it is to replace existing ones, in which case, a fast
            // path would fail more often than not.

            Entry[] tab = table;
            int len = tab.length;
            //计算出索引的位置
            int i = key.threadLocalHashCode & (len-1);

            // 从索引位置开始遍历，由于不是链表结构，
            // 因此通过nextIndex方法来寻找下一个索引的位置
            for (Entry e = tab[i];
                 //当遍历为空时结束遍历
                 e != null;
                 e = tab[i = nextIndex(i, len)]) {
                //拿到Entry的key，也就是ThreadLocal对象
                ThreadLocal<?> k = e.get();

                //改Entry的key和传入的key相等，则用传入的value替换原来的value
                if (k == key) {
                    e.value = value;
                    return;
                }

                //该Entry的key为空，则代表改Entry需要被清空
                if (k == null) {
                    //调用replaceStaleEntry，该方法继续寻找传入key的安放位置，并清理掉key为空的Entry
                    replaceStaleEntry(key, value, i);
                    return;
                }
            }

            //寻找到一个空的位置，则放置在该位置上
            tab[i] = new Entry(key, value);
            int sz = ++size;
            //是cleanSomeSlots用来清理掉key为空的Entry，如果此方法返回true，
            // 则代表知道清理掉了1个元素，则此次set必然不需要扩容，如果此方法返回false
            // 则判断sz是否大于阈值
            if (!cleanSomeSlots(i, sz) && sz >= threshold)
                rehash();
        }

        /**
         * Remove the entry for key.
         */
        private void remove(ThreadLocal<?> key) {
            Entry[] tab = table;
            int len = tab.length;
            //根据hashCode计算出当前ThreadLocal的索引位置
            int i = key.threadLocalHashCode & (len-1);
            //从位置i开始遍历，知道Entry为null
            for (Entry e = tab[i];
                 e != null;
                 e = tab[i = nextIndex(i, len)]) {
                //如果找到key相同的
                if (e.get() == key) {
                    //则调用clear方法，该方法会把key的引用清空
                    e.clear();
                    //调用expungeStaleEntry方法清除key为null的Entry
                    expungeStaleEntry(i);
                    return;
                }
            }
        }

        /**
         * Replace a stale entry encountered during a set operation
         * with an entry for the specified key.  The value passed in
         * the value parameter is stored in the entry, whether or not
         * an entry already exists for the specified key.
         *
         * As a side effect, this method expunges all stale entries in the
         * "run" containing the stale entry.  (A run is a sequence of entries
         * between two null slots.)
         *
         * @param  key the key
         * @param  value the value to be associated with key
         * @param  staleSlot index of the first stale entry encountered while
         *         searching for key.
         */
        //用指定的entry 替换 一个设置操作中陈旧的entry
        private void replaceStaleEntry(ThreadLocal<?> key, Object value,
                                       int staleSlot) {
            Entry[] tab = table;
            int len = tab.length;
            Entry e;

            // Back up to check for prior stale entry in current run.
            // We clean out whole runs at a time to avoid continual
            // incremental rehashing due to garbage collector freeing
            // up refs in bunches (i.e., whenever the collector runs).
            //清除元素的开始位置
            int slotToExpunge = staleSlot;
            //向前遍历，直到遇到Entry为空
            for (int i = prevIndex(staleSlot, len);
                 (e = tab[i]) != null;
                 i = prevIndex(i, len))
                if (e.get() == null)
                    //记录最后一个key为null的索引位置
                    slotToExpunge = i;

            // Find either the key or trailing null slot of run, whichever
            // occurs first
            //向后遍历，直到遇到Entry为空
            for (int i = nextIndex(staleSlot, len);
                 (e = tab[i]) != null;
                 i = nextIndex(i, len)) {
                ThreadLocal<?> k = e.get();

                // If we find key, then we need to swap it
                // with the stale entry to maintain hash table order.
                // The newly stale slot, or any other stale slot
                // encountered above it, can then be sent to expungeStaleEntry
                // to remove or rehash all of the other entries in run.
                //该entry的key和传入的key相等，则将传入的value替换掉该Entry的value
                if (k == key) {
                    e.value = value;

                    //将i的位置和staleSlot位置的元素对换（staleSlot位置较前，是要清除的元素）
                    tab[i] = tab[staleSlot];
                    tab[staleSlot] = e;

                    // Start expunge at preceding stale entry if it exists
                    //如果相等，则代表上面的向前寻找key为null的遍历没有找到
                    //即staleSlot位置前面的元素没有需要清除的，此时将slotToExpunge设置为i
                    //因为原staleSlot的元素已经被放到i位置了，这时位置i前面的元素都不需要清除
                    if (slotToExpunge == staleSlot)
                        slotToExpunge = i;
                    //从slotToExpunge位置开始清除key为空的entry
                    cleanSomeSlots(expungeStaleEntry(slotToExpunge), len);
                    return;
                }

                // If we didn't find stale entry on backward scan, the
                // first stale entry seen while scanning for key is the
                // first still present in the run.
                //如果第一次遍历到key为null的元素，并且上面的向前寻找key为null的遍历没有找到，
                //则将slotToExpunge设置为当前的位置
                if (k == null && slotToExpunge == staleSlot)
                    slotToExpunge = i;
            }

            //如果key没有找到。则新建一个Entry，放在staleSlot位置
            // If key not found, put new entry in stale slot
            tab[staleSlot].value = null;
            tab[staleSlot] = new Entry(key, value);

            //如果slotToExpunge!=staleSlot，代表除了staleSlot位置还有其他位置的元素需要清除
            //需要清除的定义：key为null的Entry，调用cleanSomeSlot方法清除key为null的entry
            // If there are any other stale entries in run, expunge them
            if (slotToExpunge != staleSlot)
                cleanSomeSlots(expungeStaleEntry(slotToExpunge), len);
        }

        /**
         * Expunge a stale entry by rehashing any possibly colliding entries
         * lying between staleSlot and the next null slot.  This also expunges
         * any other stale entries encountered before the trailing null.  See
         * Knuth, Section 6.4
         *
         * @param staleSlot index of slot known to have null key
         * @return the index of the next null slot after staleSlot
         * (all between staleSlot and this slot will have been checked
         * for expunging).
         */
        //从staleSlot开始，清除key为空的Entry，并将不为空的元素放到合适的位置，最后返回Entry的索引
        private int expungeStaleEntry(int staleSlot) {
            Entry[] tab = table;
            int len = tab.length;

            // expunge entry at staleSlot
            //将tab上staleSLot位置的对象清空
            tab[staleSlot].value = null;
            tab[staleSlot] = null;
            size--;

            // Rehash until we encounter null
            Entry e;
            int i;
            //遍历下一个元素，即(i+1)%len位置的
            for (i = nextIndex(staleSlot, len);
                 //遍历到Entry为空时， 跳出循环并返回索引位置
                 (e = tab[i]) != null;
                 i = nextIndex(i, len)) {
                ThreadLocal<?> k = e.get();
                //当前遍历Entry的key为空，则将该位置的对象清空
                if (k == null) {
                    e.value = null;
                    tab[i] = null;
                    size--;
                    //当前遍历Entry的key不为空
                } else {
                    //重新计算该Entry的索引位置
                    int h = k.threadLocalHashCode & (len - 1);
                    //如果索引位置不为当前索引位置
                    if (h != i) {
                        //则将i位置对象清空，替当前Entry寻找正确的位置
                        tab[i] = null;

                        // Unlike Knuth 6.4 Algorithm R, we must scan until
                        // null because multiple entries could have been stale.
                        //如果h位置不为null，则向后寻找当前Entry的位置
                        while (tab[h] != null)
                            h = nextIndex(h, len);
                        tab[h] = e;
                    }
                }
            }
            return i;
        }

        /**
         * Heuristically scan some cells looking for stale entries.
         * This is invoked when either a new element is added, or
         * another stale one has been expunged. It performs a
         * logarithmic number of scans, as a balance between no
         * scanning (fast but retains garbage) and a number of scans
         * proportional to number of elements, that would find all
         * garbage but would cause some insertions to take O(n) time.
         *
         * @param i a position known NOT to hold a stale entry. The
         * scan starts at the element after i.
         *
         * @param n scan control: {@code log2(n)} cells are scanned,
         * unless a stale entry is found, in which case
         * {@code log2(table.length)-1} additional cells are scanned.
         * When called from insertions, this parameter is the number
         * of elements, but when from replaceStaleEntry, it is the
         * table length. (Note: all this could be changed to be either
         * more or less aggressive by weighting n instead of just
         * using straight log n. But this version is simple, fast, and
         * seems to work well.)
         *
         * @return true if any stale entries have been removed.
         */
        private boolean cleanSomeSlots(int i, int n) {
            boolean removed = false;
            Entry[] tab = table;
            int len = tab.length;
            do {
                //下一个索引的位置
                i = nextIndex(i, len);
                Entry e = tab[i];
                //遍历到key为null的元素
                if (e != null && e.get() == null) {
                    //重置n的值
                    n = len;
                    //标志有移除元素
                    removed = true;
                    //移除i位置及之后key为null的元素
                    i = expungeStaleEntry(i);
                }
            } while ( (n >>>= 1) != 0);
            return removed;
        }

        /**
         * Re-pack and/or re-size the table. First scan the entire
         * table removing stale entries. If this doesn't sufficiently
         * shrink the size of the table, double the table size.
         */
        private void rehash() {
            //调用expungeStaleEntries方法清理key为空的Entry
            expungeStaleEntries();

            // Use lower threshold for doubling to avoid hysteresis
            //如果清理后size超过阈值的3/4，则进行扩容
            if (size >= threshold - threshold / 4)
                resize();
        }

        /**
         * Double the capacity of the table.
         */
        //扩容方法，数组容量 * 2
        private void resize() {
            Entry[] oldTab = table;
            int oldLen = oldTab.length;
            //新表长度为老表长度的2倍
            int newLen = oldLen * 2;
            //创建新表
            Entry[] newTab = new Entry[newLen];
            int count = 0;

            //遍历所有的元素
            for (int j = 0; j < oldLen; ++j) {
                //拿到对应位置的Entry
                Entry e = oldTab[j];
                //如果key为null，将value清空
                if (e != null) {
                    ThreadLocal<?> k = e.get();
                    //如果key为null，将value清空
                    if (k == null) {
                        e.value = null; // Help the GC
                    } else {
                        //通过hash code计算新表的索引位置
                        int h = k.threadLocalHashCode & (newLen - 1);
                        //如果新表的该位置已经有元素，则调用nextIndex方法指导寻找到空位
                        while (newTab[h] != null)
                            h = nextIndex(h, newLen);
                        //将元素放在对应的位置
                        newTab[h] = e;
                        count++;
                    }
                }
            }

            // 设置新表扩容的阈值
            setThreshold(newLen);
            //更新size
            size = count;
            //Table指向新表
            table = newTab;
        }

        /**
         * Expunge all stale entries in the table.
         */
        private void expungeStaleEntries() {
            Entry[] tab = table;
            int len = tab.length;
            for (int j = 0; j < len; j++) {
                Entry e = tab[j];
                if (e != null && e.get() == null)
                    expungeStaleEntry(j);
            }
        }
    }
}
