package org.openrepose.commons.utils.classloader.ear;

public interface EarClassLoaderContext {

    EarDescriptor getEarDescriptor();

    ClassLoader getClassLoader();
}
