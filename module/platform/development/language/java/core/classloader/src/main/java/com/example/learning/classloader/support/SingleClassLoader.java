package com.example.learning.classloader.support;

public final class SingleClassLoader extends ClassLoader {

    private final String targetName;
    private final byte[] targetBytes;

    public SingleClassLoader(ClassLoader parent, String targetName, byte[] targetBytes) {
        super(parent);
        this.targetName = targetName;
        this.targetBytes = targetBytes.clone();
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        if (!targetName.equals(name)) {
            throw new ClassNotFoundException(name);
        }
        return defineClass(name, targetBytes, 0, targetBytes.length);
    }
}
