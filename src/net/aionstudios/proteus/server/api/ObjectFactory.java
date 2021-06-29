package net.aionstudios.proteus.server.api;

import java.lang.reflect.Field;

import sun.misc.Unsafe;

public class ObjectFactory {
	
	private static Unsafe sUnsafe;
	private static ObjectFactory self = null;
	
	private ObjectFactory() {
		try {
			final Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
			unsafeField.setAccessible(true);
			sUnsafe = (Unsafe) unsafeField.get(null);
		} catch (Throwable e) {
			sUnsafe = null;
		}
	}
	
	@SuppressWarnings("unchecked")
	public final <T> T newInstance(Class<T> clss) {
		try {
			return (T)sUnsafe.allocateInstance(clss);
		} catch (InstantiationException e) {
			return null;
		}
	}
	
	public static ObjectFactory getInstance() {
		if(self==null) self = new ObjectFactory();
		return self;
	}
}
