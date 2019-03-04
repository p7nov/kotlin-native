package org.jetbrains.kotlin.backend.konan.llvm.coverage

import llvm.LLVMValueRef
import org.jetbrains.kotlin.backend.konan.Context
import org.jetbrains.kotlin.backend.konan.KonanConfigKeys
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.konan.util.removeSuffixIfPresent
import org.jetbrains.kotlin.resolve.descriptorUtil.module

/**
 * "Umbrella" class of all the of the code coverage related logic.
 */
internal class CoverageManager(val context: Context) {

    private val shouldCoverProgram: Boolean =
            context.config.configuration.getBoolean(KonanConfigKeys.COVERAGE)

    private val librariesToCover: Set<String> =
            context.config.configuration.getList(KonanConfigKeys.LIBRARIES_TO_COVER)
                    .map { it.removeSuffixIfPresent(".klib") }
                    .toSet()

    val enabled: Boolean =
            shouldCoverProgram || librariesToCover.isNotEmpty()

    private val filesRegionsInfo = mutableListOf<FileRegionInfo>()

    private fun getFunctionRegions(irFunction: IrFunction) =
            filesRegionsInfo.flatMap { it.functions }.firstOrNull { it.function == irFunction }

    private val coveredModules: Set<ModuleDescriptor> by lazy {
        val coveredUserCode = if (shouldCoverProgram) setOf(context.moduleDescriptor) else emptySet()
        val coveredLibs = context.irModules.filter { it.key in librariesToCover }.values
                .map { it.descriptor }.toSet()
        coveredLibs + coveredUserCode
    }

    private fun fileCoverageFilter(file: IrFile) =
            file.packageFragmentDescriptor.module in coveredModules

    /**
     * Walk [irModuleFragment] subtree and collect [FileRegionInfo] for files that are part of [coveredModules].
     */
    fun collectRegions(irModuleFragment: IrModuleFragment) {
        if (enabled) {
            val regions = CoverageRegionCollector(this::fileCoverageFilter).collectFunctionRegions(irModuleFragment)
            filesRegionsInfo += regions
        }
    }

    /**
     * @return [LLVMCoverageInstrumentation] instance if [irFunction] should be covered.
     */
    fun tryGetInstrumentation(irFunction: IrFunction?, callSitePlacer: (function: LLVMValueRef, args: List<LLVMValueRef>) -> Unit) =
            if (enabled && irFunction != null) {
                getFunctionRegions(irFunction)?.let { LLVMCoverageInstrumentation(context, it, callSitePlacer) }
            } else {
                null
            }

    /**
     * Add __llvm_coverage_mapping to the LLVM module.
     */
    fun writeRegionInfo() {
        if (enabled) {
            LLVMCoverageWriter(context, filesRegionsInfo).write()
        }
    }
}