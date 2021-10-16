package com.feverbrainstudios;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class RomMangler {
	
	public enum SPLIT_TYPES {
		ROM_LOAD16_BYTE, ROM_LOAD32_BYTE, ROM_LOAD16_WORD_SWAP, ROMX_LOAD_WORD_SKIP_6, 
		ROM_LOAD64_BYTE, ROM_LOAD, ROM_LOAD16_BYTE_SWAP, ROM_LOAD64_WORD, FILL
	}

	private static final int BANK_SIZE = 0x200000;
	
	private static byte[] SEQUENCE_TEMPLATE = new byte[] {0x10, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 
			 											  0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
			 											  0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
			 											  0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
			 											  0x00, 0x05, 0x0D, 0x06, (byte) 0xFF, 0x07, 0x7E, 0x1F,
			 											  0x0B, 0x08, 0x7D, 0x09, 0x03, (byte) 0xB9, 0x17};
	
	private static final int SEQUENCE_BANK_OFFSET = 0x28;
	private static final int SEQUENCE_PROGRAM_OFFSET = 0x2A;
	private static final int TRACK_POINTER = 0x21;
	
	
	public static void main(String[] arg) {

		try {
			if ("combine".equals(arg[0])) {
				combine(arg[1], arg[2]);
			} else if ("split".equals(arg[0])) {
				split(arg[1], arg[2]);
			} else if ("zipdir".equals(arg[0])) {
				zipdir(arg[1], arg[2]);
			} else if ("unzip".equals(arg[0])) {
				unzip(arg[1], arg[2]);
			} else if ("mra_patch".equals(arg[0])) {
				mra_patch(arg[1], arg[2], arg[3], arg[4]);
			} else if ("bin_to_mra_patch".equals(arg[0])) {
				bin_to_mra_patch(arg[1], arg[2], arg[3]);
			} else if ("xor_table".equals(arg[0])) {
				apply_xor_table(arg[1], arg[2]);
			} else if ("cps2_unshuffle".equals(arg[0])) {
				cps2_gfx_decode(arg[1], arg[2]);
			} else if ("cps2_reshuffle".equals(arg[0])) {
				cps2_gfx_recode(arg[1], arg[2]);
			} else if ("cps2_spot_encrypt".equals(arg[0])) {
				cps2_spot_encrypt(arg[1], arg[2], arg[3], arg[4]);
			} else if ("cps2_parse_listing".equals(arg[0])) {
				cps2_parse_listing(arg[1], arg[2], arg[3]);
			} else if ("oki_split_samples".equals(arg[0])) {
				oki_split_samples(arg[1], arg[2], arg[3], arg[4]);
			} else if ("pcm_sample_reduce_width".equals(arg[0])) {
				pcm_sample_reduce(arg[1], arg[2]);
			} else if ("pcm_sample_reduce_width_dir".equals(arg[0])) {
				pcm_sample_reduce_width_dir(arg[1], arg[2], arg[3]);
			} else if ("pcm_sample_upsample".equals(arg[0])) {
				pcm_sample_upsample(arg[1], arg[2], arg[3]);
			} else if ("cps2_gen_sequences".equals(arg[0])) {
				cps2_gen_sequences(arg[1], arg[2], arg[3], arg[4]);
			} else if ("cps2_apply_sequences".equals(arg[0])) {
				cps2_apply_sequences(arg[1], arg[2], arg[3]);
			} else if ("bin_patch".equals(arg[0])) {
				binary_patch(arg[1], arg[2], arg[3]);
			} else if ("cps1_8bpp_to_bitplanes".equals(arg[0])) {
				cps1_8bpp_to_bitplanes(arg[1], arg[2]);
			}
		} catch (RuntimeException e) {
			e.printStackTrace();
			System.out.println(e.getLocalizedMessage());
		}

	}
	
	private static void cps1_8bpp_to_bitplanes(String in, String out) {
		byte[] tile8bpp = loadRom(in);
		byte[] tile4bpp = new byte[128];
		for (int x = 0; x < 256; x +=2) {
			tile4bpp[x / 2] = (byte) (((tile8bpp[x] << 4) & 0xF0) | ((tile8bpp[x + 1]) & 0xF));
		}
		
		byte[] output = new byte[128];
		for (int x = 0; x < 128; x +=4) {
			byte[] eightPixels = cps1_bitplanes_from_pixels(tile4bpp[x], tile4bpp[x + 1], tile4bpp[x + 2], tile4bpp[x + 3]);
			patch(output, eightPixels, x);
		}
		writeRom(out, output);
	}

	private static byte[] cps1_bitplanes_from_pixels(int pix12, int pix34, int pix56, int pix78) {
		byte[] result = new byte[4];
		int current = 0;
		for (int x = 0; x < 8; x ++) {
			current += is_high_bit_set(pix78);
			current = current << 1;
			pix78 = pix78 << 1;

			current += is_high_bit_set(pix56);
			current = current << 1;
			pix56 = pix56 << 1;

			current += is_high_bit_set(pix34);
			current = current << 1;
			pix34 = pix34 << 1;

			current += is_high_bit_set(pix12);
			pix12 = pix12 << 1;

			if (x % 2 == 1) {
				result[x / 2] = (byte) (current & 0xFF);
				current = 0;
			}
		}
		
		return result;
	}
	
	private static int is_high_bit_set(int aByte) {
		return (aByte & 0x80) > 0 ? 1 : 0;
	}

	// Patch is filename, patch address in hex
	private static void binary_patch(String patches, String bin, String out) {
		byte[] data = loadRom(bin);
	    List<String> patchesList = loadTextFile(patches);
	    for (String patch: patchesList) {
	    	String[] patchSplit = patch.split(",");
	    	int location = fromHexString(patchSplit[1]);
	    	byte[] patchData = loadRom(patchSplit[0]);
	    	patch(data, patchData, location);
	    }
	    writeRom(out, data);
	    System.out.println("Wrote binary file " + out);
	}
	
	private static void patch(byte[] data, byte[] patchData, int location) {
		for (int x = 0; x < patchData.length; x ++) {
			data[location + x] = patchData[x];
		}
	}

	private static void cps2_apply_sequences(String rom, String out, String projectFile) {
		byte[] data = loadRom(rom);
		List<String> projectFileStrings = loadTextFile(projectFile);
		
		int sequenceTableStart = 0x8c05;
		int sequencesPointer = 0xbc05;
		for (String projectEntry: projectFileStrings) {
			if (projectEntry.contains("SEQUENCE")) {
				String[] split = projectEntry.split("~");

				byte[] sequenceData = loadRom(split[2]);
				int index = Integer.parseInt(split[3]);
				
				int sequenceStart = sequencesPointer;
				patch(data, sequenceData, sequencesPointer);

				int sequenceTablePointer = sequenceTableStart + index * 4;
				data[sequenceTablePointer] = 0;
				data[sequenceTablePointer + 1] = (byte) ((sequenceStart >> 16) & 0xFF);
				data[sequenceTablePointer + 2] = (byte) ((sequenceStart >> 8) & 0xFF);
				data[sequenceTablePointer + 3] = (byte) (sequenceStart & 0xFF);
				
				sequencesPointer += sequenceData.length;
			}
		}
		
		writeRom(out, data);
		
		System.out.println("Applied sequences!");
	}

	
	private static void cps2_gen_sequences(String bankString, String startString, String endString, String outDir) {
		int bank = Integer.valueOf(bankString);
		int start = Integer.valueOf(startString);
		int end = Integer.valueOf(endString);
		
		int trackPointerPositon = 0x10;
		for (int x = start; x < end; x ++) {
			SEQUENCE_TEMPLATE[SEQUENCE_BANK_OFFSET] = (byte) bank;
			SEQUENCE_TEMPLATE[SEQUENCE_PROGRAM_OFFSET] = (byte) x;
			for (int y = 0; y < 0x20; y += 2) {
				SEQUENCE_TEMPLATE[y + 1] = 0;
				SEQUENCE_TEMPLATE[y + 2] = 0;
				if (y == trackPointerPositon) {
					SEQUENCE_TEMPLATE[y + 2] = TRACK_POINTER;
				}
			}
			writeRom(outDir + "\\sound_effect_"+x+".c2m", SEQUENCE_TEMPLATE);
			trackPointerPositon +=2;
			if (trackPointerPositon >= 0x20) {
				trackPointerPositon = 0x10;
			}
		}
		System.out.println("Generated Sequences!");
	}



	private static void pcm_sample_upsample(String in8bitPcmFile, String outFile, String multiplier) {
		float mult = Float.valueOf(multiplier);
		
		byte[] samples = loadRom(in8bitPcmFile);
		byte[] outSamples = new byte[samples.length * (int) mult];
		
		for (int x = 0; x < samples.length; x ++) {
			outSamples[x * 3] = samples[x];
			float diff = 0;
			if (x + 1 < samples.length) {
				diff = ((float)samples[x+1] - (float)samples[x]) / mult; 
			}
			for (int y = 1; y < mult; y ++) {
				outSamples[x * 3 + y] = (byte) ((float) samples[x] + (diff * (float)y)); 
			}
		}
		
		writeRom(outFile, outSamples);
	}

	private static void pcm_sample_reduce_width_dir(String inDir, String outDir, String filePrefix) {
		File file = new File(inDir);

		if (!file.isDirectory()) {
			throw new RuntimeException("Second argument of zipdir should be a directory.");
		}
		
		String[] files = file.list();

		
		for (String fileName: files) {
			pcm_sample_reduce(inDir + "\\" + fileName, outDir + "\\" + filePrefix + fileName.substring(fileName.lastIndexOf('_')));
		}
	}

	
	private static void pcm_sample_reduce(String in16bitPcmFile, String out8bitPcmFile) {
		byte[] samples = loadRom(in16bitPcmFile);
		byte[] outSamples = new byte[samples.length / 2];
		
		swapBytes(samples); // Little endian conversion
		
		for (int x = 0; x < samples.length; x +=2) {
			int outInt = bytesToInt(new byte[] {samples[x], samples[x +1], 0, 0})[0];
			byte out = (byte) (outInt / 65536 / 256);
			outSamples[x/2] = out;
		}
		
		writeRom(out8bitPcmFile, outSamples);
	}

	private static void oki_split_samples(String samplesFile, String outDirectory, String adpcmCommandFile, String adpcmOutDirectory) {
		List<String> adpcmCommands = new ArrayList<String>();
		byte[] samples = loadRom(samplesFile);
		int sampleId = 0;
		for (int x = 0; x < 0x400; x += 8) {
			int start = bytesToInt(new byte[] {0, samples[x], samples[x+1], samples[x+2]})[0];
			int end = bytesToInt(new byte[] {0, samples[x+3], samples[x+4], samples[x+5]})[0];
			if (start != end) {
				int sampleLength = end - start + 1;
				byte[] sample = new byte[sampleLength];
				for (int y = 0; y < sampleLength; y ++) {
					sample[y] = samples[start + y];
				}
				String file = outDirectory + "\\oki_adpcm_" + sampleId + ".bin";
				String pcmFile = adpcmOutDirectory + "\\pcm_" + sampleId + ".bin";
				writeRom(file, sample);
				adpcmCommands.add("adpcm.exe od " + file + " " + pcmFile);
			}
			sampleId ++;
		}
		writeTextFile(adpcmCommandFile, adpcmCommands);
	}

	private static void swapBytes(byte[] bytes) {
		for (int x = 0; x < bytes.length; x += 2) {
			if (x + 1 < bytes.length) {
				byte temp = bytes[x];
				bytes[x] = bytes[x + 1];
				bytes[x + 1] = temp;
			}
		}
	}
	
	private static void cps2_spot_encrypt(String patchedRomFile, String encryptedRomFile, String out, String patchedLocations) {
		byte[] patchedRom = loadRom(patchedRomFile);
		byte[] encryptedRom = loadRom(encryptedRomFile);
	
		swapBytes(encryptedRom);
		
		List<String> patchedLocs = loadTextFile(patchedLocations);
		byte[] result = new byte[patchedRom.length];
		
		for (int x = 0; x < patchedRom.length; x ++) {
			result[x] = patchedRom[x];
		}
		
		for (String patchedLoc: patchedLocs) {
			if (!patchedLoc.trim().isEmpty() && !patchedLoc.contains(";")) {
				String[] range = patchedLoc.trim().split("\s+"); 
				int start = fromHexString(range[0]);
				int end = fromHexString(range[1]);
				for (int x = start; x < end; x ++) {
					result[x] = encryptedRom[x];
				}
			}
		}
		
		writeRom(out, result);
	}
	
	private static List<String> loadTextFile(String textFile) {
		try {
			return Files.readAllLines(new File(textFile).toPath());
		} catch (IOException e) {
			// TODO Auto-generated catch block
			throw new RuntimeException("No text file found with name: " + textFile);
		}
	}
	
	private static void writeTextFile(String textFile, List<String> text) {
		FileWriter writer;
			try {
				writer = new FileWriter(textFile);
			for(String str: text) {
			  writer.write(str + System.lineSeparator());
			}
			writer.close();
		} catch (IOException e) {
			throw new RuntimeException("Failed to write file " + textFile);
		} 
	}
	
	private static void cps2_parse_listing(String listingFile, String outFile, String typeString) {
		List<String> listingStrings = loadTextFile(listingFile);

		boolean typeList = typeString.contains("list");
		boolean encryptData = false;
		List<String> results = new ArrayList<String>();
		for (int index = 0; index < listingStrings.size(); index ++) {
			String listing = listingStrings.get(index);
			if (!listing.isEmpty()) {
				String[] split = listing.split("\s+");
				int address = fromHexString(split[0]);
				if (true) {
					boolean isCode = false;
					try {
						fromHexString(split[1]);
						isCode = true;
					} catch (Exception e) { }
					if (isCode) {
						String endAddress = listingStrings.get(index + 1).split("\s+")[0];
						int length = fromHexString(endAddress) - address;
						if (listing.contains("dc.")) {
							if (typeList) {
								if (encryptData) {
									results.add("Encrypted Data " + split[0] + " - " + endAddress + " " + getHex(length, 4));
								} else {
									results.add("Data " + split[0] + " - " + endAddress + " " + getHex(length, 4));
								}
							} else if (encryptData) {
								results.add(split[0] + " " + endAddress);
							}
						} else {
							if (typeList) {
								results.add("Code " + split[0] + " - " + endAddress + " " + getHex(length, 4));
							} else {
								results.add(split[0] + " " + endAddress);
							}
						}
					} else if (listing.contains("<encrypted_data>")) {
						encryptData = true;
					} else if (listing.contains("</encrypted_data>")) {
						encryptData = false;
					}
				}
			}
		}
		writeTextFile(outFile, results);
	}
	
	private static void unshuffle(long buf[],int start, int len) {
		int i;
		long t;

		if (len == 2)
			return;

		assert(len % 4 == 0);   /* must not happen */

		len /= 2;

		unshuffle(buf, start, len);
		unshuffle(buf, start + len, len);

		for (i = 0; i < len / 2; i++)
		{
			t = buf[start + len / 2 + i];
			buf[start + len / 2 + i] = buf[start + len + i];
			buf[start + len + i] = t;
		}
	}

	private static long[] cps2_create_decoder_table(int romSize) {
		long data[] = new long[romSize / 8];
		for (int x = 0; x < data.length; x ++) {
			data[x] = x;
		}
		for (int i = 0; i < data.length; i += BANK_SIZE / 8) {
			unshuffle(data, i, BANK_SIZE / 8);
		}
		return data;
	}
	
	private static void cps2_gfx_decode(String file, String out) {
		byte data[] = loadRom(file);
		
		int size = data.length;

		long longData[] = bytesToLongs(data);
		
		for (int i = 0; i < size; i += BANK_SIZE) {
			unshuffle(longData, i / 8, BANK_SIZE / 8);
		}
				
		writeRom(out, longsToBytes(longData));
	}
	
	private static void cps2_gfx_recode(String file, String out) {
		byte data[] = loadRom(file);
				
		long longData[] = bytesToLongs(data);
		
		long result[] = new long[longData.length];
		for (int i = 0; i < longData.length; i += BANK_SIZE / 8) {
			for (int x = 0; x < BANK_SIZE / 16; x ++) {
				result[i + x * 2] = longData[i + x];
				result[i + x * 2 + 1] = longData[i + BANK_SIZE / 16 + x];
			}
		}
		
		writeRom(out, longsToBytes(result));
	}

	private static long[] bytesToLongs(byte[] data) {
		long longData[] = new long[data.length/8];
		ByteBuffer bb = ByteBuffer.wrap(data);
		for (int x = 0; x < data.length / 8; x ++) {
			longData[x] = bb.getLong();
		}
		return longData;
	}

	private static int[] bytesToInt(byte[] data) {
		int intData[] = new int[data.length/4];
		ByteBuffer bb = ByteBuffer.wrap(data);
		for (int x = 0; x < data.length / 4; x ++) {
			intData[x] = bb.getInt();
		}
		return intData;
	}

	private static byte[] longsToBytes(long[] data) {
		ByteBuffer bb = ByteBuffer.allocate(data.length * Long.BYTES);
        bb.asLongBuffer().put(data);
        return bb.array();
	}

	private static void apply_xor_table(String string, String string2) {
		byte[] original = loadRom(string);
		byte[] xor = loadRom(string2);

		byte[] changes = new byte[original.length];
		for (int x = 0; x < original.length; x ++) {
			changes[x] = (byte) (original[x] ^ xor[x]);
		}
		
		writeRom(string + ".xor", changes);
	}

	private static void bin_to_mra_patch(String patchOffset, String byteSwap, String rom) {
		int offset = fromHexString(patchOffset);
		
		byte[] modified = loadRom(rom); // generatePatchString uses the modified data

		byte[] original = new byte[modified.length];
		byte[] changes = new byte[modified.length];

		// Use every byte in output string
		for (int x = 0; x < original.length; x ++) {
			changes[x] = 1;
		}

		if ("swap".equals(byteSwap)) {
			swapBytes(modified);
		}
		
		generatePatchStrings(offset, original, modified, changes);
	}
	
	private static void mra_patch(String patchOffset, String byteSwap, String originalRom, String modifiedRom) {
		int offset = fromHexString(patchOffset);
		
		byte[] original = loadRom(originalRom);
		byte[] modified = loadRom(modifiedRom);
		
		byte[] changes = new byte[original.length];

		for (int x = 0; x < original.length; x ++) {
			changes[x] = 0;
		}

		for (int x = 0; x < original.length; x ++) {
			if (original[x] != modified[x]) {
				changes[x] = 1;
				if (x % 2 == 1) {
					changes[x - 1] = 1;
				} else {
					changes[x + 1] = 1;
				}
			}
		}

		if ("swap".equals(byteSwap)) {
			swapBytes(modified);
		}
		
		
		generatePatchStrings(offset, original, modified, changes);
	}

	private static void generatePatchStrings(int offset, byte[] original, byte[] modified, byte[] changes) {
		String address = "";
		String patch = "";
		boolean consecutive = false;
		for (int x = 0; x < original.length; x ++) {
			if (changes[x] > 0) { 
				if (!consecutive) {
					address = "0x" + getHex(x + offset, 8);
					patch += getHex(((int)modified[x]) & 0xFF, 2);
					consecutive = true;
				} else {
					patch += " ";
					patch += getHex(((int)modified[x]) & 0xFF, 2);
				}
			} else {
				if (consecutive) {
					System.out.println("<patch offset=\"" + address +  "\">" + patch + "</patch>");
					patch = "";
					consecutive = false;
				}
			}
		}
		if (!patch.isEmpty()) {
			System.out.println("<patch offset=\"" + address +  "\">" + patch + "</patch>");
		}
	}
	
	private static String getHex(int value, int digits) {
		String result = String.format("%0" + digits + "X", value);
		if (result.length() > digits) {
			return result.substring(result.length() - digits, result.length());
		}
		return result;
	}
	
	private static int fromHexString(String string) {
		return Integer.valueOf(string.trim(), 16);
	}

	private static void unzip(String zipFile, String path) {
		path = new File(path).getAbsolutePath();
		FileInputStream inputStream;
		try {
			inputStream = new FileInputStream(zipFile);
			ZipInputStream zipStream = new ZipInputStream(inputStream);
			ZipEntry nextEntry = zipStream.getNextEntry();
			while (nextEntry != null) {
				byte[] buffer = new byte[(int) nextEntry.getSize()];
				File newFile = new File(path, nextEntry.getName());
				extractFile(zipStream, newFile);
				zipStream.closeEntry();
				nextEntry = zipStream.getNextEntry();
			}
			zipStream.close();
		} catch (FileNotFoundException e) {
			System.out.println(e.getLocalizedMessage());
			e.printStackTrace();
		} catch (IOException e) {
			System.out.println(e.getLocalizedMessage());
			e.printStackTrace();
		}
	}
	
    private static void extractFile(ZipInputStream zipIn, File file) throws IOException {
        BufferedOutputStream outstream = new BufferedOutputStream(new FileOutputStream(file));
        byte[] bytesIn = new byte[4096];
        int read = 0;
        while ((read = zipIn.read(bytesIn)) != -1) {
            outstream.write(bytesIn, 0, read);
        }
        outstream.flush();
        outstream.close();
    }

	private static void zipdir(String directory, String outputFile) {
		File file = new File(directory);

		if (!file.isDirectory()) {
			throw new RuntimeException("Second argument of zipdir should be a directory.");
		}
		
		
		File out = new File(outputFile);
		try {
			FileOutputStream outstream = new FileOutputStream(out);
			ZipOutputStream zipStream = new ZipOutputStream(outstream);
			String[] files = file.list();
			for (String inFile: files) {
				zipStream.putNextEntry(new ZipEntry(inFile));
				byte[] data = loadRom(directory + "\\" + inFile);
				zipStream.write(data);
				zipStream.closeEntry();
			}
			zipStream.flush();
			zipStream.close();
			outstream.flush();
			outstream.close();
		} catch (IOException e) {
			System.out.println(e.getLocalizedMessage());
			e.printStackTrace();
		}
	}

	private static void split(String configFile, String toSplit) {
		List<ConfigEntry> entries = loadConfig(configFile);
		byte[] rom = loadRom(toSplit);
		System.out.println("Loaded rom " + toSplit);
		for (ConfigEntry entry: entries) {
			switch(entry.type) {
			case ROM_LOAD64_WORD:
			case ROMX_LOAD_WORD_SKIP_6:
				ROMX_WRITE_WORD_SKIP_6(entry.file, entry.location, entry.length, rom);
				System.out.println("Wrote romx word skip 6 file " + entry.file);
				break;
			case ROM_LOAD:
				ROM_WRITE(entry.file, entry.location, entry.length, rom);
				System.out.println("Wrote rom file " + entry.file);
				break;
			case ROM_LOAD16_BYTE:
				ROM_WRITE_16_BYTE(entry.file, entry.location, entry.length, rom);
				System.out.println("Wrote rom 16 byte file " + entry.file);
				break;
			case ROM_LOAD16_BYTE_SWAP:
				ROM_WRITE_16_BYTE_SWAP(entry.file, entry.location, entry.length, rom);
				System.out.println("Wrote rom 16 byte swap file " + entry.file);
				break;
			case ROM_LOAD16_WORD_SWAP:
				ROM_WRITE_16_WORD_SWAP(entry.file, entry.location, entry.length, rom);
				System.out.println("Wrote rom 16 word swap file " + entry.file);
				break;
			case ROM_LOAD32_BYTE:
				ROM_WRITE_32_BYTE(entry.file, entry.location, entry.length, rom);
				System.out.println("Wrote rom 16 byte file " + entry.file);
				break;
			case ROM_LOAD64_BYTE:
				ROM_WRITE64_BYTE(entry.file, entry.location, entry.length, rom);
				System.out.println("Wrote romx word skip 6 file " + entry.file);
				break;
			case FILL:
				throw new RuntimeException("Fill is meant to be use to pad roms while combining.");
			default:
				throw new RuntimeException("Invalid entry type in split config. " + entry.type);
			}
		}
	}

	private static void ROMX_WRITE_WORD_SKIP_6(String fileString, int location, int length, byte[] rom) {
		byte[] output = new byte[length];
		int nextLocation = location;
		for (int x = 0; x < length; x += 2) {
			output[x] = rom[nextLocation];
			output[x + 1] = rom[nextLocation + 1];
			nextLocation += 8;
		}
		writeRom(fileString, output);
	}
	
	private static void ROM_WRITE64_BYTE(String fileString, int location, int length, byte[] rom) {
		byte[] output = new byte[length];
		int nextLocation = location;
		for (int x = 0; x < length; x ++) {
			output[x] = rom[nextLocation];
			nextLocation += 8;
		}
		writeRom(fileString, output);
	}
	
	
	private static void ROM_WRITE_16_WORD_SWAP(String fileString, int location, int length, byte[] rom) {
		byte[] output = new byte[length];
		for (int x = 0; x < length; x += 2) {
			output[x] = rom[location + x + 1];
			output[x + 1] = rom[location + x];
		}
		writeRom(fileString, output);
	}

	private static void ROM_WRITE_16_BYTE(String fileString, int location, int length, byte[] rom) {
		byte[] output = new byte[length];
		for (int x = 0; x < length; x ++) {
			output[x] = rom[location + x * 2];
		}
		writeRom(fileString, output);
	}

	private static void ROM_WRITE_16_BYTE_SWAP(String fileString, int location, int length, byte[] rom) {
		byte[] output = new byte[length];
		for (int x = 0; x < length; x += 2) {
			output[x] = rom[location + (x + 1) * 2];
			output[x + 1] = rom[location + x * 2];
		}
		writeRom(fileString, output);
	}
	
	private static void ROM_WRITE_32_BYTE(String fileString, int location, int length, byte[] rom) {
		byte[] output = new byte[length];
		for (int x = 0; x < length; x ++) {
			output[x] = rom[location + x * 4];
		}
		writeRom(fileString, output);
	}

	private static void ROM_WRITE(String fileString, int location, int length, byte[] rom) {
		byte[] output = new byte[length];
		for (int x = 0; x < length; x ++) {
			output[x] = rom[location + x];
		}
		writeRom(fileString, output);
	}
	
	private static void combine(String configFile, String result) {
		List<ConfigEntry> entries = loadConfig(configFile);
		int size = calcSize(entries);
		byte[] results = new byte[size];
		for (ConfigEntry entry: entries) {
			switch(entry.type) {
				case ROM_LOAD64_WORD:
				case ROMX_LOAD_WORD_SKIP_6:
					ROMX_LOAD_WORD_SKIP_6(entry.file, entry.location, entry.length, results);
					System.out.println("Read romx word skip 6 file " + entry.file);
					break;
				case ROM_LOAD:
					ROM_LOAD(entry.file, entry.location, entry.length, results);
					System.out.println("Read rom file " + entry.file);
					break;
				case ROM_LOAD16_BYTE:
					ROM_LOAD_16_BYTE(entry.file, entry.location, entry.length, results);
					System.out.println("Read rom 16 byte file " + entry.file);
					break;
				case ROM_LOAD16_BYTE_SWAP:
					ROM_LOAD_16_BYTE_SWAP(entry.file, entry.location, entry.length, results);
					System.out.println("Read rom 16 byte swap file " + entry.file);
					break;
				case ROM_LOAD16_WORD_SWAP:
					ROM_LOAD16_WORD_SWAP(entry.file, entry.location, entry.length, results);
					System.out.println("Read rom 16 word swap file " + entry.file);
					break;
				case ROM_LOAD32_BYTE:
					ROM_LOAD_32_BYTE(entry.file, entry.location, entry.length, results);
					System.out.println("Read rom 16 byte file " + entry.file);
					break;
				case ROM_LOAD64_BYTE:
					ROM_LOAD64_BYTE(entry.file, entry.location, entry.length, results);
					System.out.println("Read romx word skip 6 file " + entry.file);
					break;
				case FILL:
					FILL_WITH_ZEROS(entry.location, entry.length, results);
					break;
				default:
					throw new RuntimeException("Invalid entry type in split config. " + entry.type);
			}
		}
		writeRom(result, results);
	}

	private static void writeRom(String fileString, byte[] results) {
		File file = new File(fileString);
		try {
			FileOutputStream out = new FileOutputStream(file);
			out.write(results);
			out.flush();
			out.close();
		} catch (IOException e) {
			System.out.println(e.getLocalizedMessage());
			e.printStackTrace();
		}
	}
	

	private static byte[] loadRom(String fileString) {
		File file = new File(fileString);
		byte[] results = new byte[(int)file.length()];
		try {
			FileInputStream stream = new FileInputStream(file);
			stream.read(results);
			stream.close();
		} catch (IOException e) {
			System.out.println(e.getLocalizedMessage());
			e.printStackTrace();
		}
		return results;
	}

	private static void ROMX_LOAD_WORD_SKIP_6(String fileString, int location, int length, byte[] results) {
		byte[] loaded = loadRom(fileString);
		int nextLocation = location;
		for (int x = 0; x < length; x += 2) {
			results[nextLocation] = loaded[x];
			results[nextLocation + 1] = loaded[x + 1];
			nextLocation += 8;
		}
	}
	
	private static void ROM_LOAD64_BYTE(String fileString, int location, int length, byte[] results) {
		byte[] loaded = loadRom(fileString);
		int nextLocation = location;
		for (int x = 0; x < length; x ++) {
			results[nextLocation] = loaded[x];
			nextLocation += 8;
		}
	}
	
	private static void ROM_LOAD16_WORD_SWAP(String fileString, int location, int length, byte[] results) {
		byte[] loaded = loadRom(fileString);
		for (int x = 0; x < length; x += 2) {
			results[location + x] = loaded[x + 1];
			results[location + x + 1] = loaded[x];
		}
	}

	private static void ROM_LOAD_16_BYTE(String fileString, int location, int length, byte[] results) {
		byte[] loaded = loadRom(fileString);
		for (int x = 0; x < length; x ++) {
			results[location + x * 2] = loaded[x];
		}
	}

	private static void ROM_LOAD_16_BYTE_SWAP(String fileString, int location, int length, byte[] results) {
		byte[] loaded = loadRom(fileString);
		for (int x = 0; x < length; x += 2) {
			results[location + x * 2] = loaded[x + 1];
			results[location + x * 2 + 1] = loaded[x];
		}
	}
	
	private static void ROM_LOAD_32_BYTE(String fileString, int location, int length, byte[] results) {
		byte[] loaded = loadRom(fileString);
		for (int x = 0; x < length; x ++) {
			results[location + x * 4] = loaded[x];
		}
	}

	private static void ROM_LOAD(String fileString, int location, int length, byte[] results) {
		byte[] loaded = loadRom(fileString);
		for (int x = 0; x < length; x ++) {
			results[location + x] = loaded[x];
		}
	}
	
	private static void FILL_WITH_ZEROS(int location, int length, byte[] results) {
		for (int x = 0; x < length; x ++) {
			results[location + x] = 0;
		}
	}
	
	private static List<ConfigEntry> loadConfig(String configFile) {
		List<ConfigEntry> entries = new ArrayList<ConfigEntry>();
		File file = new File(configFile);
		try {
			FileReader reader = new FileReader(file);
			BufferedReader buff = new BufferedReader(reader);
			String read = buff.readLine();
			while (read != null) {
				String[] split = read.split(",");
				if (split.length > 3) {
					entries.add(new ConfigEntry(split[0].trim(), split[1].trim(), Integer.valueOf(split[2].trim(), 16), Integer.valueOf(split[3].trim(), 16)));
				} else {
					entries.add(new ConfigEntry(split[0].trim(), "", Integer.valueOf(split[1].trim(), 16), Integer.valueOf(split[2].trim(), 16)));
				}
				read = buff.readLine();
			}
			buff.close();
			reader.close();
		} catch (IOException e) {
			System.out.println(e.getLocalizedMessage());
			e.printStackTrace();
		}
		return entries;
	}
	
	private static int calcSize(List<ConfigEntry> entries) {
		int max = 0;
		for (ConfigEntry entry: entries) {
			int size = 0;
			switch(entry.type) {
				case ROM_LOAD64_WORD:
				case ROMX_LOAD_WORD_SKIP_6:
					size = entry.length * 4 + ((entry.location >> 4) << 4);
					break;
				case ROM_LOAD:
					size = entry.length + entry.location;
					break;
				case ROM_LOAD16_BYTE:
					size = entry.length * 2 + entry.location;
					break;
				case ROM_LOAD16_BYTE_SWAP:
					size = entry.length * 2 + entry.location;
					break;
				case ROM_LOAD16_WORD_SWAP:
					size = entry.length + entry.location;
					break;
				case ROM_LOAD32_BYTE:
					size = entry.length * 4 + entry.location;
					break;
				case ROM_LOAD64_BYTE:
					size = entry.length * 8 + ((entry.location >> 4) << 4);
					break;
				case FILL:
					size = entry.length + entry.location;
					break;
				default:
					throw new RuntimeException("Invalid entry type in split config. " + entry.type);
			}
			
			if (size > max) {
				max = size;
			}
		}
		return max;
	}
	
	private static class ConfigEntry {
		public SPLIT_TYPES type;
		public String file;
		public int location;
		public int length;
		
		public ConfigEntry(String type, String file, int location, int length) {
			this.type = SPLIT_TYPES.valueOf(type);
			this.file = file;
			this.location = location;
			this.length = length;
		}
	}
}
