package ch.ethz.globis.phtree;

import java.io.Serializable;

/*
This file is part of ELKI:
Environment for Developing KDD-Applications Supported by Index-Structures

Copyright (C) 2011-2015
Eidgenössische Technische Hochschule Zürich (ETH Zurich)
Institute for Information Systems
GlobIS Group

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

/**
 * Interface for PhIterator filters. A checker is continuously checked 
 * during navigation to see whether subnodes or postfixes should be traversed. 
 *
 * This interface needs to be serializable because in the distributed version of the PhTree, 
 * it is send from the client machine to the server machine.
 * 
 * @author Tilmann Zäschke
 *
 */
public interface PhFilter extends Serializable {

  /**
   * 
	 * @param key the key to check
   * @return True if the key passes the filter.
   */
	boolean isValid(long[] key);
	
	/**
	 * 
	 * @param bitsToIgnore trailing bits to ignore
	 * @param prefix the prefix to check
	 * @return False if key with the given prefix cannot pass the filter, otherwise true.
	 */
	boolean isValid(int bitsToIgnore, long[] prefix);
	
}
