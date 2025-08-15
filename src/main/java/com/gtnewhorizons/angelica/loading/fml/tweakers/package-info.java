/**
 * ITweaker needs to be in a standalone package
 * because FML adds the whole package to the class
 * loader exclusions and that causes really hard to
 * find bugs. ITweakers also get class loaded
 * by the parent class loader and not LaunchClassLoader,
 * as a result the ITweakers should not reference any
 * class otherwise it will load them (maybe a second time)
 * on the parent class loader.
 */
package com.gtnewhorizons.angelica.loading.fml.tweakers;
