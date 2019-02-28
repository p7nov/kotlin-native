package org.jetbrains.kotlin.backend.konan.llvm.coverage

import kotlinx.cinterop.*
import llvm.*
import org.jetbrains.kotlin.backend.konan.Context
import org.jetbrains.kotlin.backend.konan.llvm.symbolName
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.declarations.name

private fun RegionKind.toLLVMCoverageRegionKind(): LLVMCoverageRegionKind = when (this) {
    RegionKind.Code -> LLVMCoverageRegionKind.CODE
    RegionKind.Gap -> LLVMCoverageRegionKind.GAP
    is RegionKind.Expansion -> LLVMCoverageRegionKind.EXPANSION
}

private fun LLVMCoverageRegion.populateFrom(region: Region, regionId: Int, filesIndex: Map<IrFile, Int>) = apply {
    fileId = filesIndex.getValue(region.file)
    lineStart = region.startLine
    columnStart = region.startColumn
    lineEnd = region.endLine
    columnEnd = region.endColumn
    counterId = regionId
    kind = region.kind.toLLVMCoverageRegionKind()
    expandedFileId = if (region.kind is RegionKind.Expansion) filesIndex.getValue(region.kind.expandedFile) else 0
}

/**
 * Writes all of the coverage information to the [org.jetbrains.kotlin.backend.konan.Context.llvmModule].
 * See http://llvm.org/docs/CoverageMappingFormat.html for the format description.
 */
internal class LLVMCoverageWriter(
        private val context: Context,
        private val filesRegionsInfo: List<FileRegionInfo>
) {
    fun write() {
        if (filesRegionsInfo.isEmpty()) return

        val module = context.llvmModule
                ?: error("LLVM module should be initialized.")
        val filesIndex = filesRegionsInfo.mapIndexed { index, fileRegionInfo -> fileRegionInfo.file to index }.toMap()

        val coverageGlobal = memScoped {
            // TODO: Each record contains char* which should be freed.
            val (functionMappingRecords, functionCoverages) = filesRegionsInfo.flatMap { it.functions }.map { functionRegions ->
                val regions = (functionRegions.regions.values).map { region ->
                    alloc<LLVMCoverageRegion>().populateFrom(region, functionRegions.regionEnumeration.getValue(region), filesIndex).ptr
                }
                val fileIds = functionRegions.regions.map { filesIndex.getValue(it.value.file) }.toSet().toIntArray()
                val functionCoverage = LLVMWriteCoverageRegionMapping(
                        fileIds.toCValues(), fileIds.size.signExtend(),
                        regions.toCValues(), regions.size.signExtend())

                val functionMappingRecord = LLVMAddFunctionMappingRecord(LLVMGetModuleContext(context.llvmModule),
                        functionRegions.function.symbolName, functionRegions.structuralHash, functionCoverage)!!

                Pair(functionMappingRecord, functionCoverage)
            }.unzip()

            val (filenames, fileIds) = filesIndex.entries.toList().map { it.key.name to it.value }.unzip()

            LLVMCoverageEmit(module, functionMappingRecords.toCValues(), functionMappingRecords.size.signExtend(),
                        filenames.toCStringArray(this), fileIds.toIntArray().toCValues(), fileIds.size.signExtend(),
                        functionCoverages.map { it.ptr }.toCValues(), functionCoverages.size.signExtend())!!
        }
        context.llvm.usedGlobals.add(coverageGlobal)
    }
}
