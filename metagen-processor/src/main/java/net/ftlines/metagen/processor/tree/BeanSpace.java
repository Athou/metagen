/**
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package net.ftlines.metagen.processor.tree;

import java.util.HashMap;
import java.util.Map;

import javax.lang.model.element.TypeElement;

import net.ftlines.metagen.processor.util.Optional;

public class BeanSpace implements Node
{
	private final Map<TypeElement, TopLevelBean> beans = new HashMap<TypeElement, TopLevelBean>();

	public void add(TypeElement element)
	{
		recursiveGetOrAdd(element, true);
	}

	public Optional<AbstractBean> get(TypeElement element)
	{
		return Optional.of(recursiveGetOrAdd(element, false));
	}

	private AbstractBean recursiveGetOrAdd(TypeElement element, boolean add)
	{
		switch (element.getNestingKind())
		{
			case TOP_LEVEL :
				TopLevelBean node = beans.get(element);
				if (node == null && add)
				{
					node = new TopLevelBean(element);
					beans.put(element, node);
				}
				return node;
			case MEMBER :
				AbstractBean parent = recursiveGetOrAdd((TypeElement)element.getEnclosingElement(), add);
				NestedBean nested = parent.getNestedBeans().get(element);
				if (nested == null && add)
				{
					nested = new NestedBean(element);
					parent.getNestedBeans().put(element, nested);
				}
				return nested;
			default :
				throw new IllegalStateException("Tried to create bean node for element: " + element +
					" with unsupported nesting kind: " + element.getNestingKind());
		}
	}

	@Override
	public void accept(Visitor visitor)
	{
		visitor.enterBeanSpace(this);
		for (TopLevelBean node : AbstractBean.copyValues(beans))
		{
			node.accept(visitor);
		}
		visitor.exitBeanSpace(this);
	}

	public void remove(TypeElement element)
	{
		beans.remove(element);
	}

}
