package org.zoodb.index.critbit;

/*
This file is part of ELKI:
Environment for Developing KDD-Applications Supported by Index-Structures

Copyright (C) 2009-2015
Tilmann Zaeschke
The author can be contacted via email: zoodb@gmx.de
https://github.com/tzaeschke/critbit

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU Affero General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU Affero General Public License for more details.

You should have received a copy of the GNU Affero General Public License
along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/

import org.zoodb.index.critbit.CritBit.FullIterator;
import org.zoodb.index.critbit.CritBit.QueryIterator;

/**
 * 
 * @author Tilmann Zaeschke
 * 
 * @param <V> The type of the value associated with each key 
 */
public interface CritBit1D<V> {

	/* @see CritBit#put(long[], Object) */
	V put(long[] key, V value);

	/* @see CritBit#contains(long[]) */
	boolean contains(long[] key);

	/* @see CritBit#query(long[], long[]) */
	QueryIterator<V> query(long[] min, long[] max);

	/* @see CritBit#size() */
	int size();

	/* @see CritBit#remove(long[]) */
	V remove(long[] key);

	/* @see CritBit#printTree() */
	void printTree();

	/* @see CritBit#get(long[]) */
	V get(long[] key);

	/* @see CritBit#iterator() */
	FullIterator<V> iterator();
}
