/**
 * Package that contains classes that may be loaded both by RFB and FML classloaders, the code contained in this package
 * should not reference any code from outside of this package to avoid causing hard to debug classloading issues unless
 * the code is known to be safe with different classloaders.
 */
package com.gtnewhorizons.angelica.loading.shared;
