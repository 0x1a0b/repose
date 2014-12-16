package org.openrepose.core.services.classloader;

import org.openrepose.commons.utils.Destroyable;
import org.openrepose.commons.utils.classloader.ear.EarClassLoader;
import org.openrepose.commons.utils.classloader.ear.EarClassLoaderContext;

import java.util.Collection;

public interface ClassLoaderManagerService extends Destroyable {

    EarClassLoader getApplication(String contextName);
    boolean hasFilter(String contextName);
    Collection<EarClassLoaderContext> getLoadedApplications();
}
