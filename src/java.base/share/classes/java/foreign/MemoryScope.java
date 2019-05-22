/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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

package java.foreign;

import jdk.internal.foreign.MemoryScopeImpl;

import java.util.Optional;

/**
 * A scope models a unit of resource lifecycle management. It provides primitives for memory allocation, as well
 * as a basic ownership model for allocating resources (e.g. pointers). Each scope has a parent scope (except the
 * global scope, which acts as root of the ownership model).
 * <p>
 *  A scope supports two terminal operation: first, a scope
 * can be closed (see {@link MemoryScope#close()}), which implies that all resources associated with that scope can be reclaimed; secondly,
 * a scope can be merged into the parent scope (see {@link MemoryScope#merge()}). After a terminal operation, a scope will no longer be available
 * for allocation.
 * <p>
 * Scope supports the {@link AutoCloseable} interface which enables thread-confided scopes to be used in conjunction
 * with the try-with-resources construct.
 */
public interface MemoryScope extends AutoCloseable {

    /**
     * Allocate region of memory with given {@code LayoutType}.
     * <p>
     * This is equivalent to the following code:
     * <blockquote><pre>{@code
allocate(layout.bitsSize() / 8, layout.alignmentInBits() / 8).baseAddress();
     * }</pre></blockquote>
     *
     * @param layout the memory layout to be allocated.
     * @return the newly allocated memory region.
     * @throws IllegalArgumentException if the specified layout has illegal size or alignment constraints.
     * @throws RuntimeException if the specified size is too large for the system runtime.
     * @throws OutOfMemoryError if the allocation is refused by the system runtime.
     */
    default MemoryAddress allocate(Layout layout) throws IllegalArgumentException, RuntimeException, OutOfMemoryError {
        if (layout.bitsSize() % 8 != 0) {
            throw new IllegalArgumentException("Layout bits size must be a multiple of 8");
        } else if (layout.alignmentBits() % 8 != 0) {
            throw new IllegalArgumentException("Layout alignment bits must be a multiple of 8");
        }
        return allocate(layout.bitsSize() / 8, layout.alignmentBits() / 8).baseAddress();
    }

    /**
     * Allocate an unaligned memory segment with given size (expressed in bits).
     * <p>
     * This is equivalent to the following code:
     * <blockquote><pre>{@code
allocate(bitsSize, 1).baseAddress();
     * }</pre></blockquote>
     *
     * @param bytesSize the size (expressed in bytes) of the memory segment to be allocated.
     * @return the newly allocated memory segment.
     * @throws IllegalArgumentException if specified size is &lt; 0.
     * @throws RuntimeException if the specified size is too large for the system runtime.
     * @throws OutOfMemoryError if the allocation is refused by the system runtime.
     */
    default MemorySegment allocate(long bytesSize) throws IllegalArgumentException, RuntimeException, OutOfMemoryError {
        return allocate(bytesSize, 1);
    }

    /**
     * Allocate a memory segment with given size (expressed in bits) and alignment constraints (also expressed in bits).
     * @param bytesSize the size (expressed in bits) of the memory segment to be allocated.
     * @param alignmentBytes the alignment constraints (expressed in bits) of the memory segment to be allocated.
     * @return the newly allocated memory segment.
     * @throws IllegalArgumentException if either specified size or alignment are &lt; 0, or if the alignment constraint
     * is not a power of 2.
     * @throws RuntimeException if the specified size is too large for the system runtime.
     * @throws OutOfMemoryError if the allocation is refused by the system runtime.
     */
    MemorySegment allocate(long bytesSize, long alignmentBytes) throws IllegalArgumentException, RuntimeException, OutOfMemoryError;

    /**
     * The parent of this scope.
     * @return the parent of this scope.
     */
    MemoryScope parent();

    /**
     * Returns the confinement thread (if any).
     * @return the confinement thread (if any).
     */
    Optional<Thread> confinementThread();

    /**
     * Closes this scope. All associated resources will be freed as a result of this operation.
     * Any existing resources (e.g. pointers) associated with this scope will no longer be accessible.
     *  As this is a terminal operation, this scope will no longer be available for further allocation.
     */
    @Override
    void close();

    /**
     * Copies all resources of this scope to the parent scope. As this is a terminal operation, this scope will no
     * longer be available for further allocation.
     */
    void merge();

    /**
     * Create a scope whose parent is the current scope.
     * @return the new scope.
     */
    MemoryScope fork();

    /**
     * Create a scope whose parent is the current scope, with given charateristics.
     * @param charateristics bitmask of the scope properties.
     * @return the new scope.
     */
    MemoryScope fork(long charateristics);

    /**
     * Retrieves the global scope associated with this VM.
     * @return the global scope.
     */
    static MemoryScope globalScope() {
        SecurityManager security = System.getSecurityManager();
        if (security != null) {
            security.checkPermission(new RuntimePermission("java.foreign.memory.MemoryScope", "globalScope"));
        }
        return MemoryScopeImpl.GLOBAL; //FIXME
    }

    /**
     * Returns the set of characteristics associated with this scope. Values can be {@link MemoryScope#ADDRESS_SERIALIZABLE},
     * {@link MemoryScope#EXECUTABLE}, {@link MemoryScope#UNALIGNED_ACCESS} and {@link MemoryScope#IMMUTABLE}. In the general case,
     * charateristics can only be made stricter when going from a parent scope to a children scope (see {@link MemoryScope#fork(long)}.
     * A notable exception to this is rule are {@link MemoryScope#IMMUTABLE} - which can be both enforced and relaxed by children to support
     * various immutability life-cycles, and {@link MemoryScope#PINNED} which can only ever be set in global scopes.
     * @return a rapresentation of the characteristics.
     */
    long characteristics();

    /**
     * Whether addresses generated by this scope can be serialized into memory. If unset, cannot be set during fork
     * (see {@link MemoryScope#fork()}.
     */
    long ADDRESS_SERIALIZABLE = 1;

    /**
     * Whether addresses generated by this scope can point to executable code. If unset, cannot be set during fork
     * (see {@link MemoryScope#fork()}.
     */
    long EXECUTABLE = ADDRESS_SERIALIZABLE << 1;

    /**
     * Whether accesses at addresses generated by this scope can be unaligned. If unset, cannot be set during fork
     * (see {@link MemoryScope#fork()}.
     */
    long UNALIGNED_ACCESS = EXECUTABLE << 1;

    /**
     * Whether addresses generated by this scope are read-only. Can be set or unset during fork.
     * This allows to create a temporary writeable scope which can then be merged (see {@link MemoryScope#merge()} into
     * an immutable parent scope; conversely, it allows to create immutable children scopes of mutable parent scopes. Under
     * this scenario, an immutable children of a mutable parent cannot be merged into it (doing so will effectively make
     * all pointers of the immutable scope writeable again) - the only terminal operation supported in such a scenario
     * is {@link MemoryScope#close}.
     */
    long IMMUTABLE = UNALIGNED_ACCESS << 1;

    /**
     * Whether this scope rejects terminal operations (see {@link MemoryScope#close()}, {@link MemoryScope#merge()}.
     */
    long PINNED = IMMUTABLE << 1;

    /**
     * Whether this scope ignores liveness checks. Can be useful in performance-sensitive context, where a user
     * might want to trade off safety for maximum performances.
     */
    long UNCHECKED = PINNED << 1;

    /**
     * Whether this scope allows access confined to given thread. Can be useful to restrict access to resources
     * managed by this scope to a single thread, to prevent memory races.
     */
    long CONFINED = UNCHECKED << 1;
}
