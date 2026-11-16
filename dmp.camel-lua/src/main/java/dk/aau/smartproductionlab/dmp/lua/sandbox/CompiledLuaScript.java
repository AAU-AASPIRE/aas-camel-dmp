/* Copyright (C) 2026 the aas-camel-dmp Authors
 * SPDX-License-Identifier: MIT */
package dk.aau.smartproductionlab.dmp.lua.sandbox;

import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaClosure;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Prototype;
import org.luaj.vm2.compiler.LuaC;

/**
 * A Lua blob compiled ONCE.
 *
 * <p>
 * The expensive part of running an AIMC blob is compiling the source into
 * LuaJ bytecode (LuaC parse + Prototype). The blob is identical for every
 * evaluation of a mapping, so the DMP compiles it a single time and reuses
 * the resulting {@link Prototype}.
 *
 * <p>
 * LuaJ {@code Globals} are not thread-safe and blobs may mutate their
 * environment, so each invocation still gets a FRESH sandbox
 * ({@link LuaSandbox#createGlobals()}); the compiled prototype is merely
 * bound to it:
 *
 * <pre>{@code
 * CompiledLuaScript compiled = CompiledLuaScript.compile(source, name);
 * Globals globals = LuaSandbox.createGlobals();
 * LuaTimeoutHook.install(globals, timeout);
 * CompiledLuaScript.runChunk(compiled.prototype(), globals); // defines aimc_main
 * globals.get("aimc_main").invoke(sources);
 * }</pre>
 *
 * <p>
 * The prototype is immutable once compiled and safe to share across threads
 * (each thread builds its own {@link LuaClosure} over it).
 *
 * @author aas-camel-dmp
 */
public final class CompiledLuaScript {

	private final Prototype prototype;

	private CompiledLuaScript(Prototype prototype) {
		this.prototype = prototype;
	}

	/** Compiles a blob into a reusable prototype. */
	public static CompiledLuaScript compile(String source, String chunkName) {
		// Compilation must not execute anything, so a bare Globals with the Lua
		// compiler installed is enough (no whitelisted libs needed at this stage).
		Globals compileGlobals = new Globals();
		LuaC.install(compileGlobals);

		LuaValue chunk = compileGlobals.load(source, "@" + chunkName);
		if (!(chunk instanceof LuaClosure)) {
			throw new IllegalArgumentException(
					"Lua blob '" + chunkName + "' did not compile to a closure");
		}
		return new CompiledLuaScript(((LuaClosure) chunk).p);
	}

	public Prototype prototype() {
		return prototype;
	}

	/**
	 * Runs the top-level chunk of the pre-compiled prototype against a fresh
	 * environment (defines {@code aimc_main} in the given globals).
	 */
	public static void runChunk(Prototype prototype, Globals globals) {
		new LuaClosure(prototype, globals).call();
	}
}
