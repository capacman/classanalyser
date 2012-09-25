package com.globalmaksimum.classanalyser;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import org.apache.bcel.classfile.Code;
import org.apache.bcel.classfile.JavaClass;
import org.apache.bcel.classfile.Method;
import org.apache.bcel.generic.ConstantPoolGen;
import org.apache.bcel.generic.InstructionHandle;
import org.apache.bcel.generic.InstructionList;
import org.apache.bcel.generic.InvokeInstruction;
import org.apache.bcel.generic.ObjectType;
import org.apache.bcel.generic.Type;
import org.apache.bcel.util.ClassLoaderRepository;
import org.apache.bcel.util.Repository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Hello world!
 * 
 */
public class App {
	private final static Logger LOGGER = LoggerFactory.getLogger(App.class);

	public static class ClassDependencyDefinition {
		private final JavaClass javaClass;
		private final List<MethodDependencyDefinition> methodDependencyDefinitions;

		public ClassDependencyDefinition(JavaClass javaClass,
				List<MethodDependencyDefinition> methodDependencyDefinitions) {
			this.javaClass = javaClass;
			this.methodDependencyDefinitions = Collections
					.unmodifiableList(methodDependencyDefinitions);
		}

		public JavaClass getJavaClass() {
			return javaClass;
		}

		public List<MethodDependencyDefinition> getMethodDependencyDefinitions() {
			return methodDependencyDefinitions;
		}

		@Override
		public String toString() {
			StringBuilder strb = new StringBuilder();
			strb.append("<class>").append("<name>")
					.append(javaClass.getClassName()).append("</name>");
			for (MethodDependencyDefinition methodDependencyDefinition : methodDependencyDefinitions) {
				strb.append(methodDependencyDefinition.toString());
			}
			strb.append("</class>");
			return strb.toString();
		}
	}

	public static class MethodDependencyDefinition {
		private final Method method;
		private final Map<InvocationDependencyPair, List<Integer>> dependencies;

		public MethodDependencyDefinition(Method method) {
			this.method = method;
			this.dependencies = new HashMap<App.InvocationDependencyPair, List<Integer>>();
		}

		public Method getMethod() {
			return method;
		}

		public void addDependency(InvocationDependencyPair pair,
				Integer lineNumber) {
			if (!dependencies.containsKey(pair))
				dependencies.put(pair, new ArrayList<Integer>());
			dependencies.get(pair).add(lineNumber);
		}

		@Override
		public String toString() {
			StringBuilder strb = new StringBuilder();
			strb.append("<method>");
			strb.append("<name>")
					.append(method.getName().equals("<init>") ? "constructor"
							: method.getName()).append("</name>");
			for (Entry<InvocationDependencyPair, List<Integer>> entry : dependencies
					.entrySet()) {
				strb.append("<invocation>").append("<referenceType>")
						.append(entry.getKey().getObjectTypeName())
						.append("</referenceType>").append("<instructionName>")
						.append(entry.getKey().getInstructionName())
						.append("</instructionName>");
				for (Integer lineNumber : entry.getValue()) {
					strb.append("<line>").append(lineNumber).append("</line>");
				}
				strb.append("</invocation>");
			}
			strb.append("</method>");
			return strb.toString();

		}
	}

	public static class InvocationDependencyPair {
		private final String objectTypeName;
		private final String instructionName;

		public InvocationDependencyPair(String objectTypeName,
				String instructionName) {
			this.objectTypeName = objectTypeName;
			this.instructionName = instructionName.equals("<init>") ? "constructor"
					: instructionName;
		}

		public String getObjectTypeName() {
			return objectTypeName;
		}

		public String getInstructionName() {
			return instructionName;
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime
					* result
					+ ((instructionName == null) ? 0 : instructionName
							.hashCode());
			result = prime
					* result
					+ ((objectTypeName == null) ? 0 : objectTypeName.hashCode());
			return result;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			InvocationDependencyPair other = (InvocationDependencyPair) obj;
			if (instructionName == null) {
				if (other.instructionName != null)
					return false;
			} else if (!instructionName.equals(other.instructionName))
				return false;
			if (objectTypeName == null) {
				if (other.objectTypeName != null)
					return false;
			} else if (!objectTypeName.equals(other.objectTypeName))
				return false;
			return true;
		}

	}

	public static void main(String[] args) throws ClassNotFoundException,
			IOException {
		// analyzeWithBcel();
		String jarFile = "/home/capacman/cluster.jar";
		List<String> classes = getClasses(jarFile);
		ClassLoader classLoader = getClassLoader(jarFile);
		System.out.println("<out>");
		for (String klazz : classes) {
			try {
				ClassDependencyDefinition classDependencyDefinition = analyzeClass(
						klazz, classLoader);

				System.out.println(classDependencyDefinition);

			} catch (ClassNotFoundException e) {

			}
		}
		System.out.println("</out>");

	}

	protected static List<String> getClasses(String fileName) {
		JarFile jarFile = null;
		try {
			jarFile = new JarFile(fileName);
			Enumeration<JarEntry> entries = jarFile.entries();
			List<String> classNames = new ArrayList<String>();
			while (entries.hasMoreElements()) {
				JarEntry jarEntry = entries.nextElement();
				if (jarEntry.getName().endsWith(".class")) {
					classNames.add(jarEntry
							.getName()
							.replaceAll(File.separator, ".")
							.substring(
									0,
									jarEntry.getName().length()
											- ".class".length()));
				}
			}
			return classNames;
		} catch (IOException e) {
			LOGGER.error("jarFile error", e);
			return Collections.emptyList();
		} finally {
			if (jarFile != null)
				try {
					jarFile.close();
				} catch (IOException e) {
					// do nothing
				}
		}
	}

	private static ClassLoader getClassLoader(String path)
			throws MalformedURLException {
		return URLClassLoader.newInstance(
				new URL[] { new URL("file://" + path) },
				App.class.getClassLoader());

	}

	protected static ClassDependencyDefinition analyzeClass(String className,
			ClassLoader classLoader) throws ClassNotFoundException {
		Repository repository = new ClassLoaderRepository(classLoader);
		JavaClass javaClass = repository.loadClass(className);
		List<MethodDependencyDefinition> dependencyDefinitions = new ArrayList<App.MethodDependencyDefinition>();
		for (Method method : javaClass.getMethods()) {
			dependencyDefinitions.add(analyzeMethod(method));
		}
		return new ClassDependencyDefinition(javaClass, dependencyDefinitions);
	}

	private static MethodDependencyDefinition analyzeMethod(Method method) {
		MethodDependencyDefinition methodDependencyDefinition = new MethodDependencyDefinition(
				method);
		Code code = method.getCode();
		ConstantPoolGen cpg = new ConstantPoolGen(code.getConstantPool());
		InstructionList instructionList = new InstructionList(code.getCode());
		instructionList.setPositions();
		for (InstructionHandle instructionHandle : instructionList
				.getInstructionHandles()) {
			if (instructionHandle.getInstruction() instanceof InvokeInstruction) {
				InvokeInstruction invokeInstruction = (InvokeInstruction) instructionHandle
						.getInstruction();
				Type type = invokeInstruction.getReferenceType(cpg);
				if (type instanceof ObjectType) {
					ObjectType reference = (ObjectType) type;
					methodDependencyDefinition.addDependency(
							new InvocationDependencyPair(reference
									.getClassName(), invokeInstruction
									.getMethodName(cpg)),
							code.getLineNumberTable().getSourceLine(
									instructionHandle.getPosition()));
				}
			}
		}
		return methodDependencyDefinition;
	}
}
