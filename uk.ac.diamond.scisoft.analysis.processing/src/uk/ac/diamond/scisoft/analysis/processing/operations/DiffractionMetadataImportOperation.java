package uk.ac.diamond.scisoft.analysis.processing.operations;

import uk.ac.diamond.scisoft.analysis.dataset.IDataset;
import uk.ac.diamond.scisoft.analysis.io.IDiffractionMetadata;
import uk.ac.diamond.scisoft.analysis.io.NexusDiffractionMetaReader;
import uk.ac.diamond.scisoft.analysis.monitor.IMonitor;
import uk.ac.diamond.scisoft.analysis.processing.AbstractOperation;
import uk.ac.diamond.scisoft.analysis.processing.OperationData;
import uk.ac.diamond.scisoft.analysis.processing.OperationException;
import uk.ac.diamond.scisoft.analysis.processing.OperationRank;

public class DiffractionMetadataImportOperation extends AbstractOperation<DiffractionMetadataImportModel, OperationData> {

	IDiffractionMetadata metadata;
	
	@Override
	public String getId() {
		return "uk.ac.diamond.scisoft.analysis.processing.operations.DiffractionMetadataImportOperation";
	}

	@Override
	public OperationData execute(IDataset slice, IMonitor monitor)
			throws OperationException {
		
		if (metadata == null) {
			NexusDiffractionMetaReader reader = new NexusDiffractionMetaReader(((DiffractionMetadataImportModel)model).getFilePath());
			IDiffractionMetadata md = reader.getDiffractionMetadataFromNexus(null);
			if (!reader.isPartialRead()) throw new OperationException(this, "File does not contain metadata");
			metadata = md;
		}
		
		slice.addMetadata(metadata);
		return new OperationData(slice);
	}


	@Override
	public OperationRank getInputRank() {
		return OperationRank.TWO;
	}

	@Override
	public OperationRank getOutputRank() {
		return OperationRank.TWO;
	}

}
