package org.ftlines.metagen.eclipse;

import java.lang.reflect.Modifier;
import java.util.Stack;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.IAnnotation;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IField;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.EnumDeclaration;
import org.eclipse.jdt.core.dom.IAnnotationBinding;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.ftlines.metagen.eclipse.model.Bean;
import org.ftlines.metagen.eclipse.model.BeanContainer;
import org.ftlines.metagen.eclipse.model.Constants;
import org.ftlines.metagen.eclipse.model.ErrorCollector;
import org.ftlines.metagen.eclipse.model.Property;
import org.ftlines.metagen.eclipse.model.Visibility;

/**
 * Metagen's view of a ICompilationUnit
 * 
 * @author igor
 * 
 */
public class BeanUnit extends BeanContainer {

	private final ICompilationUnit compilationUnit;

	public BeanUnit(ICompilationUnit compilationUnit) {
		this.compilationUnit = compilationUnit;
	}

	@Override
	public String toString() {
		return "[BeanUnit cu=" + compilationUnit.toString() + "]";
	}

	public ICompilationUnit getCompilationUnit() {
		return compilationUnit;
	}

	/**
	 * Checks if the type will generate a meta in the most efficient manner. This method should return {@code true} unless it is absolutely
	 * sure this compilation unit will not generate meta.
	 * 
	 * @return
	 * @throws JavaModelException
	 */
	public boolean canPossiblyGenerateMeta() throws JavaModelException {
		FqnResolver resolver = new FqnResolver(compilationUnit);
		for (IType type : compilationUnit.getAllTypes()) {

			boolean skip = false;

			for (IAnnotation annotation : type.getAnnotations()) {
				if (resolver.is(annotation, Constants.GENERATED)) {
					// we know generated types will not produce meta
					return false;
				}
				if (resolver.is(annotation, Constants.IGNORE)) {
					skip = true;
					break;
				}
				if (resolver.isAny(annotation, Constants.META, Constants.BEAN, Constants.ENTITY, Constants.MAPPEDSUPERCLASS)) {
					return true;
				} else if (resolver.is(annotation, Constants.ENTITY)) {
					return true;
				}
			}

			if (skip) {
				continue;
			}

			for (IField field : type.getFields()) {
				for (IAnnotation annotation : field.getAnnotations()) {
					if (resolver.is(annotation, Constants.PROPERTY)) {
						return true;
					}
				}
			}

			for (IMethod method : type.getMethods()) {
				for (IAnnotation annotation : method.getAnnotations()) {
					if (resolver.is(annotation, Constants.PROPERTY)) {
						return true;
					}
				}
			}
		}
		return false;
	}

	public void discover(ASTNode ast, ErrorCollector errors) {
		ast.accept(new Visitor(errors));
	}

	public class Visitor extends ASTVisitor {
		private final Stack<Bean> beans = new Stack<Bean>();
		private final ErrorCollector errors;

		private Visitor(ErrorCollector errors) {
			this.errors = errors;
		}

		private void visitBean(ITypeBinding type) {

			Bean bean = new Bean(getVisibility(type.getModifiers()), type.getQualifiedName());
			beans.push(bean);
			if (!populateBean(type, bean)) {
				return;
			}

			// check if we need to extend a super meta class
			ITypeBinding cursor = type.getSuperclass();
			while (cursor != null) {
				Bean superbean = new Bean(getVisibility(cursor.getModifiers()), cursor.getQualifiedName());
				if (!populateBean(cursor, superbean)) {
					break;
				}
				if (superbean.willGenerateMeta()) {
					String supername = getMetaSuperName(cursor);
					bean.setSuperclass(supername);
					break;
				}
				cursor = cursor.getSuperclass();
			}
		}

		/**
		 * Build the name of the meta superclass. For example, given <code>B</code> in <code>foo.bar.A.B</code> it will return
		 * <code>foo.bar.AMeta.BMeta</code>
		 * 
		 * @param type
		 * @return
		 */
		private String getMetaSuperName(ITypeBinding type) {
			String supername = "";
			ITypeBinding cursor = type;
			while (cursor != null) {
				supername = "." + cursor.getErasure().getName() + "Meta" + supername;
				cursor = cursor.getDeclaringClass();
			}
			supername = type.getPackage().getName() + supername;
			return supername;
		}

		private boolean populateBean(ITypeBinding type, Bean bean) {
			boolean autobean = false;

			// scan type annotations

			for (IAnnotationBinding annotation : type.getAnnotations()) {
				String name = annotation.getAnnotationType().getQualifiedName();
				if (Constants.META.equals(name)) {
					bean.setForced(true);
				} else if (Constants.BEAN.equals(name)) {
					autobean = true;
				} else if (Constants.ENTITY.equals(name)) {
					autobean = true;
				} else if (Constants.MAPPEDSUPERCLASS.equals(name)) {
					autobean = true;
				} else if (Constants.GENERATED.equals(name)) {
					// this is a generated class, we dont need to process it for metagen annotations
					return false;
				} else if (Constants.IGNORE.equals(name)) {
					// this class is marked as ignored, skip it
					return false;
				}
			}

			// discover fields that can be properties

			for (IVariableBinding field : type.getDeclaredFields()) {
				boolean property = false;
				boolean deprecated = false;
				if (autobean && getVisibility(field.getModifiers()) == Visibility.PUBLIC) {
					property = true;
				} else {
					for (IAnnotationBinding annotation : field.getAnnotations()) {
						String name = annotation.getAnnotationType().getQualifiedName();
						if (Constants.PROPERTY.equals(name)) {
							if ((field.getModifiers() & Modifier.PRIVATE) > 0) {
								errors.error(compilationUnit.getResource(), 1, "Private fields (" + field.getName()
										+ ") do not support @Property annotations");
							} else {
								property = true;
							}
						} else if (Constants.DEPRECATED.equals(name)) {
							deprecated = true;
						}
					}
				}

				if (property) {
					ITypeBinding fieldType = field.getType();
					String fieldTypeName = new TypeResolver(fieldType).resolve();
					Property prop = new Property(getVisibility(field.getModifiers()), field.getDeclaringClass().getQualifiedName(),
							fieldTypeName, field.getName());
					prop.setDeprecated(deprecated);
					prop.setFieldName(field.getName());
					bean.addProperty(prop);
				}
			}

			// discover getters that can be properties

			for (IMethodBinding method : type.getDeclaredMethods()) {

				String name = method.getName();

				// we are only interested in getters

				if (method.getParameterTypes().length > 0) {
					continue;
				}

				boolean set = name.length() > 3 && name.startsWith("get") && Character.isUpperCase(name.charAt(3));
				boolean is = name.length() > 2 && name.startsWith("is") && Character.isUpperCase(name.charAt(2));

				if (!set && !is) {
					continue;
				}

				name = set ? Character.toLowerCase(name.charAt(3)) + name.substring(4) : Character.toLowerCase(name.charAt(2))
						+ name.substring(3);

				// scan annotations of the method

				boolean property = false;
				boolean deprecated = false;
				for (IAnnotationBinding annotation : method.getAnnotations()) {
					String annot = annotation.getAnnotationType().getQualifiedName();
					if (Constants.PROPERTY.equals(annot)) {
						property = true;
					} else if (Constants.DEPRECATED.equals(annot)) {
						deprecated = true;
					}
				}

				if (autobean || property) {

					// add a property for this method

					Property prop = bean.getProperty(name);
					ITypeBinding returnType = method.getReturnType();
					String typeName = new TypeResolver(returnType).resolve();
					if (prop == null) {
						prop = new Property(getVisibility(method.getModifiers()), method.getDeclaringClass().getQualifiedName(), typeName,
								name);
						bean.addProperty(prop);
					} else {
						prop.relaxVisibility(getVisibility(method.getModifiers()));
						// prefer type of the getter
						prop.setType(typeName);
					}
					prop.setDeprecated(deprecated);
					prop.setGetterName(method.getName());

				}
			}

			// discover property setters

			for (IMethodBinding method : type.getDeclaredMethods()) {

				String name = method.getName();

				// we are only interested in setters

				if (!(name.length() > 3 && name.startsWith("set") && Character.isUpperCase(name.charAt(3)))) {
					continue;
				}

				if (method.getParameterTypes().length != 1) {
					continue;
				}

				name = Character.toLowerCase(name.charAt(3)) + name.substring(4);
				Property property = bean.getProperty(name);
				if (property != null) {

					String typeName = new TypeResolver(method.getParameterTypes()[0]).resolve();
					if (typeName.equals(property.getType())) {
						property.setSetterName(method.getName());
						property.relaxVisibility(getVisibility(method.getModifiers()));
					}
				}
			}

			return true;
		}

		private void endVisitBean() {
			Bean bean = beans.pop();

			if (bean.getVisibility() == Visibility.PRIVATE) {
				return;
			}

			// TODO this check should be moved into Bean - Bean.isEmpty()
			if (bean.willGenerateMeta()) {
				if (beans.size() > 0) {
					beans.peek().add(bean);
				} else {
					BeanUnit.this.add(bean);
				}
			}
		}

		@Override
		public boolean visit(TypeDeclaration node) {
			visitBean(node.resolveBinding());
			return true;
		}

		@Override
		public void endVisit(TypeDeclaration node) {
			endVisitBean();
		}

		@Override
		public boolean visit(EnumDeclaration node) {
			visitBean(node.resolveBinding());
			return true;
		}

		@Override
		public void endVisit(EnumDeclaration node) {
			endVisitBean();
		}

	}

	public boolean isEmpty() {
		return getBeans() == null || getBeans().isEmpty();
	}

	public String generate(ICompilationUnit compilationUnit, IFile file) throws CoreException {

		StringBuilder source = new StringBuilder();

		String pack = compilationUnit.getParent().getElementName();
		if (pack != null && pack.length() > 0) {
			source.append("package " + pack + ";\n\n");
		}

		for (Bean bean : getBeans()) {
			generate(bean, source, false);
		}

		return source.toString();
	}

	public void generate(Bean bean, StringBuilder source, boolean nested) {
		source.append("@SuppressWarnings(\"all\")\n");
		source.append("@javax.annotation.Generated(\"metagen\")\n");
		source.append(bean.getVisibility().getTerm());
		if (nested) {
			source.append(" static ");
		}
		source.append(" class " + bean.getSimpleName() + "Meta ");
		if (bean.getSuperclass() != null) {
			source.append(" extends " + bean.getSuperclass());
		}
		source.append("{\n");

		source.append("public static class C {\n");
		source.append("public static final String name=\"" + bean.getName() + "\";\n");
		source.append("public static final String simpleName=\"" + bean.getSimpleName() + "\";\n");
		source.append("}\n");

		if (bean.getProperties() != null) {

			for (Property property : bean.getProperties().values()) {

				if (property.isDeprecated()) {
					source.append("@Deprecated ");
				}

				source.append(property.getVisibility().getTerm());
				source.append(" static final ").append(Constants.SINGULAR_PROPERTY);
				source.append("<").append(bean.getName()).append(", ").append(property.getBoxedType()).append("> ");
				source.append(property.getSafeName()).append(" = new ").append(Constants.SINGULAR_PROPERTY);
				// source.append("<").append(bean.getName()).append(", ").append(property.getBoxedType()).append(">");
				source.append("(\"").append(property.getName()).append("\", " + property.getOwner() + ".class");

				source.append(", ");
				if (property.getFieldName() != null) {
					source.append("\"").append(property.getName()).append("\"");
				} else {
					source.append("null");
				}

				source.append(", ");
				if (property.getGetterName() != null) {
					source.append("\"").append(property.getGetterName()).append("\"");
				} else {
					source.append("null");
				}

				source.append(", ");
				if (property.getSetterName() != null) {
					source.append("\"").append(property.getSetterName()).append("\"");
				} else {
					source.append("null");
				}

				source.append(");\n");

			}

			source.append("public static class P {\n");
			for (Property property : bean.getProperties().values()) {
				source.append("public static final String " + property.getSafeName() + "=\"" + property.getName() + "\";\n");
			}
			source.append("}\n");
		}

		if (bean.getBeans() != null) {
			for (Bean inner : bean.getBeans()) {
				generate(inner, source, true);
			}
		}

		source.append("}");

	}

	private static Visibility getVisibility(int modifiers) {
		if ((modifiers & Modifier.PUBLIC) > 0) {
			return Visibility.PUBLIC;
		} else if ((modifiers & Modifier.PROTECTED) > 0) {
			return Visibility.PROTECTED;
		} else if ((modifiers & Modifier.PRIVATE) > 0) {
			return Visibility.PRIVATE;
		} else {
			return Visibility.DEFAULT;
		}
	}

}
