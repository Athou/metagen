/**
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

package net.ftlines.metagen.processor.annot.ignore;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.FileNotFoundException;

import net.ftlines.metagen.processor.MetaPackageTest;

import org.junit.Test;

public class IgnoreTest extends MetaPackageTest {

	@Test
	public void test() {
		assertTrue(result.isClean());
		try {
			assertTrue(result.getMetaSource(Bean.class).exists());
			fail("Beans with @Ignore should not produce a meta class");
		} catch (FileNotFoundException e) {
			// expected
		}
	}
}