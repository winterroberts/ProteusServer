package net.winrob.proteus.server.api;

import java.lang.reflect.Field;

import sun.misc.Unsafe;

/**
 * A class used to instantiate other classes without calling their constructors.
 * 
 * @author Winter Roberts
 *
 */
public class ObjectInstantiator {
	
	private static Unsafe sUnsafe;
	private static ObjectInstantiator self = null;
	
	private ObjectInstantiator() {
		try {
			final Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
			unsafeField.setAccessible(true);
			sUnsafe = (Unsafe) unsafeField.get(null);
		} catch (Throwable e) {
			sUnsafe = null;
		}
	}
	
	/**
	 * Creates a new instance of the given class as an object (which has been allocated, but not constructed).
	 * 
	 * @param <T> The runtime class type which need to be allocated.
	 * @param clss The class.
	 * @return A new instance of the given class.
	 */
	@SuppressWarnings("unchecked")
	public final <T> T newInstance(Class<T> clss) {
		try {
			return (T)sUnsafe.allocateInstance(clss);
		} catch (InstantiationException e) {
			return null;
		}
	}
	
	/**
	 * @return A singleton instance of the instantiator.
	 */
	public static ObjectInstantiator getInstance() {
		if(self==null) self = new ObjectInstantiator();
		return self;
	}
}
