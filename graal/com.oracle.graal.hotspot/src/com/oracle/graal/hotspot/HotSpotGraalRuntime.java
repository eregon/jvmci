/*
 * Copyright (c) 2011, 2012, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.graal.hotspot;

import static com.oracle.graal.compiler.GraalDebugConfig.*;
import static com.oracle.graal.compiler.common.GraalOptions.*;
import static com.oracle.graal.compiler.common.UnsafeAccess.*;
import static com.oracle.graal.hotspot.CompileTheWorld.Options.*;
import static com.oracle.graal.hotspot.HotSpotGraalRuntime.Options.*;
import static com.oracle.graal.hotspot.InitTimer.*;
import static sun.reflect.Reflection.*;

import java.lang.reflect.*;
import java.util.*;

import sun.misc.*;
import sun.reflect.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.code.stack.*;
import com.oracle.graal.api.collections.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.api.replacements.*;
import com.oracle.graal.api.runtime.*;
import com.oracle.graal.compiler.common.*;
import com.oracle.graal.compiler.target.*;
import com.oracle.graal.debug.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.hotspot.CompileTheWorld.Config;
import com.oracle.graal.hotspot.bridge.*;
import com.oracle.graal.hotspot.debug.*;
import com.oracle.graal.hotspot.events.*;
import com.oracle.graal.hotspot.logging.*;
import com.oracle.graal.hotspot.meta.*;
import com.oracle.graal.options.*;
import com.oracle.graal.printer.*;
import com.oracle.graal.replacements.*;
import com.oracle.graal.runtime.*;

//JaCoCo Exclude

/**
 * Singleton class holding the instance of the {@link GraalRuntime}.
 */
public final class HotSpotGraalRuntime implements GraalRuntime, RuntimeProvider, StackIntrospection {

    private static final HotSpotGraalRuntime instance;

    static {
        try (InitTimer t = timer("initialize HotSpotOptions")) {
            HotSpotOptions.initialize();
        }

        try (InitTimer t = timer("HotSpotGraalRuntime.<init>")) {
            instance = new HotSpotGraalRuntime();
        }

        try (InitTimer t = timer("HotSpotGraalRuntime.completeInitialization")) {
            // Why deferred initialization? See comment in completeInitialization().
            instance.completeInitialization();
        }

        registerFieldsToFilter(HotSpotGraalRuntime.class, "instance");
    }

    /**
     * Gets the singleton {@link HotSpotGraalRuntime} object.
     */
    @CallerSensitive
    public static HotSpotGraalRuntime runtime() {
        SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            Class<?> cc = Reflection.getCallerClass();
            if (cc != null && cc.getClassLoader() != null) {
                sm.checkPermission(Graal.ACCESS_PERMISSION);
            }
        }
        assert instance != null;
        return instance;
    }

    /**
     * Do deferred initialization.
     */
    public void completeInitialization() {

        // Proxies for the VM/Compiler interfaces cannot be initialized
        // in the constructor as proxy creation causes static
        // initializers to be executed for all the types involved in the
        // proxied methods. Some of these static initializers (e.g. in
        // HotSpotMethodData) rely on the static 'instance' field being set
        // to retrieve configuration details.
        CompilerToVM toVM = this.compilerToVm;

        if (CountingProxy.ENABLED) {
            toVM = CountingProxy.getProxy(CompilerToVM.class, toVM);
        }
        if (Logger.ENABLED) {
            toVM = LoggingProxy.getProxy(CompilerToVM.class, toVM);
        }

        this.compilerToVm = toVM;

        TTY.initialize(Options.LogFile.getStream());

        if (Log.getValue() == null && Meter.getValue() == null && Time.getValue() == null && Dump.getValue() == null) {
            if (MethodFilter.getValue() != null) {
                TTY.println("WARNING: Ignoring MethodFilter option since Log, Meter, Time and Dump options are all null");
            }
        }

        if (Debug.isEnabled()) {
            DebugEnvironment.initialize(LogFile.getStream());

            String summary = DebugValueSummary.getValue();
            if (summary != null) {
                switch (summary) {
                    case "Name":
                    case "Partial":
                    case "Complete":
                    case "Thread":
                        break;
                    default:
                        throw new GraalInternalError("Unsupported value for DebugSummaryValue: %s", summary);
                }
            }
        }

        // Complete initialization of backends
        try (InitTimer st = timer(hostBackend.getTarget().arch.getName(), ".completeInitialization")) {
            hostBackend.completeInitialization();
        }
        for (HotSpotBackend backend : backends.values()) {
            if (backend != hostBackend) {
                try (InitTimer st = timer(backend.getTarget().arch.getName(), ".completeInitialization")) {
                    backend.completeInitialization();
                }
            }
        }

        BenchmarkCounters.initialize(toVM);

        runtimeStartTime = System.nanoTime();
    }

    public static class Options {

        // @formatter:off
        @Option(help = "The runtime configuration to use")
        static final OptionValue<String> GraalRuntime = new OptionValue<>("");

        @Option(help = "File to which logging is sent.  %p in the name will be replaced with a string the process, usually the process id.")
        public static final PrintStreamOption LogFile = new PrintStreamOption();
        // @formatter:on
    }

    private static HotSpotBackendFactory findFactory(String architecture) {
        HotSpotBackendFactory basic = null;
        HotSpotBackendFactory selected = null;
        HotSpotBackendFactory nonBasic = null;
        int nonBasicCount = 0;

        for (HotSpotBackendFactory factory : Services.load(HotSpotBackendFactory.class)) {
            if (factory.getArchitecture().equalsIgnoreCase(architecture)) {
                if (factory.getGraalRuntimeName().equals(GraalRuntime.getValue())) {
                    assert selected == null || checkFactoryOverriding(selected, factory);
                    selected = factory;
                }
                if (factory.getGraalRuntimeName().equals("basic")) {
                    assert basic == null || checkFactoryOverriding(basic, factory);
                    basic = factory;
                } else {
                    nonBasic = factory;
                    nonBasicCount++;
                }
            }
        }

        if (selected != null) {
            return selected;
        } else {
            if (!GraalRuntime.getValue().equals("")) {
                // Fail fast if a non-default value for GraalRuntime was specified
                // and the corresponding factory is not available
                throw new GraalInternalError("Specified runtime \"%s\" not available for the %s architecture", GraalRuntime.getValue(), architecture);
            } else if (nonBasicCount == 1) {
                // If there is exactly one non-basic runtime, select this one.
                return nonBasic;
            } else {
                return basic;
            }
        }
    }

    /**
     * Checks that a factory overriding is valid. A factory B can only override/replace a factory A
     * if the B.getClass() is a subclass of A.getClass(). This models the assumption that B is
     * extends the behavior of A and has therefore understood the behavior expected of A.
     *
     * @param baseFactory
     * @param overridingFactory
     */
    private static boolean checkFactoryOverriding(HotSpotBackendFactory baseFactory, HotSpotBackendFactory overridingFactory) {
        return baseFactory.getClass().isAssignableFrom(overridingFactory.getClass());
    }

    /**
     * Gets the kind of a word value on the {@linkplain #getHostBackend() host} backend.
     */
    public static Kind getHostWordKind() {
        return instance.getHostBackend().getTarget().wordKind;
    }

    /**
     * Reads a word value from a given address.
     */
    public static long unsafeReadWord(long address) {
        return unsafe.getAddress(address);
    }

    /**
     * Reads a klass pointer from a constant object.
     */
    public static long unsafeReadKlassPointer(Object object) {
        return instance.getCompilerToVM().readUnsafeKlassPointer(object);
    }

    /**
     * Reads a word value from a given object.
     */
    public static long unsafeReadWord(Object object, long offset) {
        if (getHostWordKind() == Kind.Long) {
            return unsafe.getLong(object, offset);
        }
        return unsafe.getInt(object, offset) & 0xFFFFFFFFL;
    }

    protected/* final */CompilerToVM compilerToVm;

    protected final HotSpotVMConfig config;
    private final HotSpotBackend hostBackend;

    /**
     * Graal mirrors are stored as a {@link ClassValue} associated with the {@link Class} of the
     * type. This data structure stores both {@link HotSpotResolvedObjectType} and
     * {@link HotSpotResolvedPrimitiveType} types.
     */
    private final ClassValue<ResolvedJavaType> graalMirrors = new ClassValue<ResolvedJavaType>() {
        @Override
        protected ResolvedJavaType computeValue(Class<?> javaClass) {
            if (javaClass.isPrimitive()) {
                Kind kind = Kind.fromJavaClass(javaClass);
                return new HotSpotResolvedPrimitiveType(kind);
            } else {
                return new HotSpotResolvedObjectType(javaClass);
            }
        }
    };

    private final Map<Class<? extends Architecture>, HotSpotBackend> backends = new HashMap<>();

    private HotSpotGraalRuntime() {
        CompilerToVM toVM = new CompilerToVMImpl();
        compilerToVm = toVM;
        try (InitTimer t = timer("HotSpotVMConfig<init>")) {
            config = new HotSpotVMConfig(compilerToVm);
        }

        CompileTheWorld.Options.overrideWithNativeOptions(config);

        // Only set HotSpotPrintCompilation and HotSpotPrintInlining if they still have their
        // default value (false).
        if (HotSpotPrintCompilation.getValue() == false) {
            HotSpotPrintCompilation.setValue(config.printCompilation);
        }
        if (HotSpotPrintInlining.getValue() == false) {
            HotSpotPrintInlining.setValue(config.printInlining);
        }
        if (HotSpotCIPrintCompilerName.getValue() == false) {
            HotSpotCIPrintCompilerName.setValue(config.printCompilerName);
        }

        if (Boolean.valueOf(System.getProperty("graal.printconfig"))) {
            printConfig(config);
        }

        String hostArchitecture = config.getHostArchitectureName();

        HotSpotBackendFactory factory;
        try (InitTimer t = timer("find factory:", hostArchitecture)) {
            factory = findFactory(hostArchitecture);
        }
        try (InitTimer t = timer("create backend:", hostArchitecture)) {
            hostBackend = registerBackend(factory.createBackend(this, null));
        }

        String[] gpuArchitectures = getGPUArchitectureNames(compilerToVm);
        for (String arch : gpuArchitectures) {
            try (InitTimer t = timer("find factory:", arch)) {
                factory = findFactory(arch);
            }
            if (factory == null) {
                throw new GraalInternalError("No backend available for specified GPU architecture \"%s\"", arch);
            }
            try (InitTimer t = timer("create backend:", arch)) {
                registerBackend(factory.createBackend(this, hostBackend));
            }
        }

        try (InitTimer t = timer("createEventProvider")) {
            eventProvider = createEventProvider();
        }
    }

    private HotSpotBackend registerBackend(HotSpotBackend backend) {
        Class<? extends Architecture> arch = backend.getTarget().arch.getClass();
        HotSpotBackend oldValue = backends.put(arch, backend);
        assert oldValue == null : "cannot overwrite existing backend for architecture " + arch.getSimpleName();
        return backend;
    }

    /**
     * Gets the Graal mirror for a {@link Class} object.
     *
     * @return the {@link HotSpotResolvedJavaType} corresponding to {@code javaClass}
     */
    public ResolvedJavaType fromClass(Class<?> javaClass) {
        return graalMirrors.get(javaClass);
    }

    /**
     * Gets the names of the supported GPU architectures for the purpose of finding the
     * corresponding {@linkplain HotSpotBackendFactory backend} objects.
     */
    private static String[] getGPUArchitectureNames(CompilerToVM c2vm) {
        String gpuList = c2vm.getGPUs();
        if (!gpuList.isEmpty()) {
            String[] gpus = gpuList.split(",");
            return gpus;
        }
        return new String[0];
    }

    private static void printConfig(HotSpotVMConfig config) {
        Field[] fields = config.getClass().getDeclaredFields();
        Map<String, Field> sortedFields = new TreeMap<>();
        for (Field f : fields) {
            f.setAccessible(true);
            sortedFields.put(f.getName(), f);
        }
        for (Field f : sortedFields.values()) {
            try {
                Logger.info(String.format("%9s %-40s = %s", f.getType().getSimpleName(), f.getName(), Logger.pretty(f.get(config))));
            } catch (Exception e) {
            }
        }
    }

    public HotSpotVMConfig getConfig() {
        return config;
    }

    public TargetDescription getTarget() {
        return hostBackend.getTarget();
    }

    public CompilerToVM getCompilerToVM() {
        return compilerToVm;
    }

    /**
     * Converts a name to a Java type.
     *
     * @param name a well formed Java type in {@linkplain JavaType#getName() internal} format
     * @param accessingType the context of resolution (may be null)
     * @param resolve force resolution to a {@link ResolvedJavaType}. If true, this method will
     *            either return a {@link ResolvedJavaType} or throw an exception
     * @return a Java type for {@code name} which is guaranteed to be of type
     *         {@link ResolvedJavaType} if {@code resolve == true}
     * @throws LinkageError if {@code resolve == true} and the resolution failed
     */
    public JavaType lookupType(String name, HotSpotResolvedObjectType accessingType, boolean resolve) {
        // If the name represents a primitive type we can short-circuit the lookup.
        if (name.length() == 1) {
            Kind kind = Kind.fromPrimitiveOrVoidTypeChar(name.charAt(0));
            return HotSpotResolvedPrimitiveType.fromKind(kind);
        }

        // Handle non-primitive types.
        Class<?> accessingClass = null;
        if (accessingType != null) {
            accessingClass = accessingType.mirror();
        }

        // Resolve the type in the VM.
        final long metaspaceKlass = compilerToVm.lookupType(name, accessingClass, resolve);
        if (metaspaceKlass == 0) {
            return HotSpotUnresolvedJavaType.create(name);
        }
        return HotSpotResolvedObjectType.fromMetaspaceKlass(metaspaceKlass);
    }

    public HotSpotProviders getHostProviders() {
        return getHostBackend().getProviders();
    }

    @Override
    public String getName() {
        return getClass().getSimpleName();
    }

    private final NodeCollectionsProvider nodeCollectionsProvider = new DefaultNodeCollectionsProvider();

    private final EventProvider eventProvider;

    private EventProvider createEventProvider() {
        if (config.flightRecorder) {
            Iterable<EventProvider> sl = Services.load(EventProvider.class);
            EventProvider singleProvider = null;
            for (EventProvider ep : sl) {
                assert singleProvider == null : String.format("multiple %s service implementations found: %s and %s", EventProvider.class.getName(), singleProvider.getClass().getName(),
                                ep.getClass().getName());
                singleProvider = ep;
            }
            return singleProvider;
        }
        return new EmptyEventProvider();
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T getCapability(Class<T> clazz) {
        if (clazz == RuntimeProvider.class) {
            return (T) this;
        } else if (clazz == CollectionsProvider.class || clazz == NodeCollectionsProvider.class) {
            return (T) nodeCollectionsProvider;
        } else if (clazz == StackIntrospection.class) {
            return (T) this;
        } else if (clazz == SnippetReflectionProvider.class) {
            return (T) getHostProviders().getSnippetReflection();
        } else if (clazz == MethodHandleAccessProvider.class) {
            return (T) getHostProviders().getMethodHandleAccess();
        } else if (clazz == EventProvider.class) {
            return (T) eventProvider;
        }
        return null;
    }

    public HotSpotBackend getHostBackend() {
        return hostBackend;
    }

    public <T extends Architecture> Backend getBackend(Class<T> arch) {
        assert arch != Architecture.class;
        return backends.get(arch);
    }

    public Map<Class<? extends Architecture>, HotSpotBackend> getBackends() {
        return Collections.unmodifiableMap(backends);
    }

    /**
     * The offset from the origin of an array to the first element.
     *
     * @return the offset in bytes
     */
    public static int getArrayBaseOffset(Kind kind) {
        switch (kind) {
            case Boolean:
                return Unsafe.ARRAY_BOOLEAN_BASE_OFFSET;
            case Byte:
                return Unsafe.ARRAY_BYTE_BASE_OFFSET;
            case Char:
                return Unsafe.ARRAY_CHAR_BASE_OFFSET;
            case Short:
                return Unsafe.ARRAY_SHORT_BASE_OFFSET;
            case Int:
                return Unsafe.ARRAY_INT_BASE_OFFSET;
            case Long:
                return Unsafe.ARRAY_LONG_BASE_OFFSET;
            case Float:
                return Unsafe.ARRAY_FLOAT_BASE_OFFSET;
            case Double:
                return Unsafe.ARRAY_DOUBLE_BASE_OFFSET;
            case Object:
                return Unsafe.ARRAY_OBJECT_BASE_OFFSET;
            default:
                throw GraalInternalError.shouldNotReachHere();
        }
    }

    /**
     * The scale used for the index when accessing elements of an array of this kind.
     *
     * @return the scale in order to convert the index into a byte offset
     */
    public static int getArrayIndexScale(Kind kind) {
        switch (kind) {
            case Boolean:
                return Unsafe.ARRAY_BOOLEAN_INDEX_SCALE;
            case Byte:
                return Unsafe.ARRAY_BYTE_INDEX_SCALE;
            case Char:
                return Unsafe.ARRAY_CHAR_INDEX_SCALE;
            case Short:
                return Unsafe.ARRAY_SHORT_INDEX_SCALE;
            case Int:
                return Unsafe.ARRAY_INT_INDEX_SCALE;
            case Long:
                return Unsafe.ARRAY_LONG_INDEX_SCALE;
            case Float:
                return Unsafe.ARRAY_FLOAT_INDEX_SCALE;
            case Double:
                return Unsafe.ARRAY_DOUBLE_INDEX_SCALE;
            case Object:
                return Unsafe.ARRAY_OBJECT_INDEX_SCALE;
            default:
                throw GraalInternalError.shouldNotReachHere();
        }
    }

    public Iterable<InspectedFrame> getStackTrace(ResolvedJavaMethod[] initialMethods, ResolvedJavaMethod[] matchingMethods, int initialSkip) {
        final long[] initialMetaMethods = toMeta(initialMethods);
        final long[] matchingMetaMethods = toMeta(matchingMethods);
        class StackFrameIterator implements Iterator<InspectedFrame> {

            private HotSpotStackFrameReference current = compilerToVm.getNextStackFrame(null, initialMetaMethods, initialSkip);
            // we don't want to read ahead if hasNext isn't called
            private boolean advanced = true;

            public boolean hasNext() {
                update();
                return current != null;
            }

            public InspectedFrame next() {
                update();
                advanced = false;
                return current;
            }

            private void update() {
                if (!advanced) {
                    current = compilerToVm.getNextStackFrame(current, matchingMetaMethods, 0);
                    advanced = true;
                }
            }
        }
        return new Iterable<InspectedFrame>() {
            public Iterator<InspectedFrame> iterator() {
                return new StackFrameIterator();
            }
        };
    }

    private static long[] toMeta(ResolvedJavaMethod[] methods) {
        if (methods == null) {
            return null;
        } else {
            long[] result = new long[methods.length];
            for (int i = 0; i < result.length; i++) {
                result[i] = ((HotSpotResolvedJavaMethod) methods[i]).getMetaspaceMethod();
            }
            return result;
        }
    }

    private long runtimeStartTime;

    /**
     * Take action related to entering a new execution phase.
     *
     * @param phase the execution phase being entered
     */
    static void phaseTransition(String phase) {
        CompilationStatistics.clear(phase);
    }

    /**
     * Called from the VM.
     */
    @SuppressWarnings("unused")
    private void compileTheWorld() throws Throwable {
        int iterations = CompileTheWorld.Options.CompileTheWorldIterations.getValue();
        for (int i = 0; i < iterations; i++) {
            getCompilerToVM().resetCompilationStatistics();
            TTY.println("CompileTheWorld : iteration " + i);
            CompileTheWorld ctw = new CompileTheWorld(CompileTheWorldClasspath.getValue(), new Config(CompileTheWorldConfig.getValue()), CompileTheWorldStartAt.getValue(),
                            CompileTheWorldStopAt.getValue(), CompileTheWorldVerbose.getValue());
            ctw.compile();
        }
        System.exit(0);
    }

    /**
     * Shuts down the runtime.
     *
     * Called from the VM.
     */
    @SuppressWarnings("unused")
    private void shutdown() throws Exception {
        new DebugValuesPrinter().printDebugValues(ResetDebugValuesAfterBootstrap.getValue() ? "application" : null, false);
        phaseTransition("final");

        SnippetCounter.printGroups(TTY.out().out());
        BenchmarkCounters.shutdown(getCompilerToVM(), runtimeStartTime);
    }
}