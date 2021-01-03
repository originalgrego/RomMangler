package com.feverbrainstudios;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class RomMangler {
	public static void main(String[] arg) {
		if (arg.length != 3) {
			System.out.println("Wrong number of args " + arg.length + "\r\n Command: \r\n " + String.join(" ", arg));
			throw new RuntimeException("Wrong number of args");
		}
		
		try {
			if ("combine".equals(arg[0])) {
				combine(arg[1], arg[2]);
			} else if ("split".equals(arg[0])) {
				split(arg[1], arg[2]);
			} else if ("zipdir".equals(arg[0])) {
				zipdir(arg[1], arg[2]);
			} else if ("unzip".equals(arg[0])) {
				unzip(arg[1], arg[2]);
			}
		} catch (RuntimeException e) {
			e.printStackTrace();
			System.out.println(e.getLocalizedMessage());
		}

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
			if ("ROM_LOAD16_BYTE".equals(entry.type)) {
				ROM_WRITE_16_BYTE(entry.file, entry.location, entry.length, rom);
				System.out.println("Wrote rom 16 byte file " + entry.file);
			} else if ("ROM_LOAD32_BYTE".equals(entry.type)) {
				ROM_WRITE_32_BYTE(entry.file, entry.location, entry.length, rom);
				System.out.println("Wrote rom 16 byte file " + entry.file);
			} else if ("ROM_LOAD16_WORD_SWAP".equals(entry.type)) {
				ROM_WRITE_16_WORD_SWAP(entry.file, entry.location, entry.length, rom);
				System.out.println("Wrote rom 16 word swap file " + entry.file);
			} else if ("ROMX_LOAD_WORD_SKIP_6".equals(entry.type)) {
				ROMX_WRITE_WORD_SKIP_6(entry.file, entry.location, entry.length, rom);
				System.out.println("Wrote romx word skip 6 file " + entry.file);
			} else if ("ROM_LOAD64_BYTE".equals(entry.type)) {
				ROM_WRITE64_BYTE(entry.file, entry.location, entry.length, rom);
				System.out.println("Wrote romx word skip 6 file " + entry.file);
			} else if ("ROM_LOAD".equals(entry.type)) {
				ROM_WRITE(entry.file, entry.location, entry.length, rom);
				System.out.println("Wrote rom file " + entry.file);
			} else if ("ROM_LOAD16_BYTE_SWAP".equals(entry.type)) {
				ROM_WRITE_16_BYTE_SWAP(entry.file, entry.location, entry.length, rom);
				System.out.println("Wrote rom 16 byte swap file " + entry.file);
			} else {
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
			if ("ROM_LOAD16_BYTE".equals(entry.type)) {
				ROM_LOAD_16_BYTE(entry.file, entry.location, entry.length, results);
				System.out.println("Read rom 16 byte file " + entry.file);
			} else if ("ROM_LOAD32_BYTE".equals(entry.type)) {
				ROM_LOAD_32_BYTE(entry.file, entry.location, entry.length, results);
				System.out.println("Read rom 16 byte file " + entry.file);
			} else if ("ROM_LOAD16_WORD_SWAP".equals(entry.type)) {
				ROM_LOAD16_WORD_SWAP(entry.file, entry.location, entry.length, results);
				System.out.println("Read rom 16 word swap file " + entry.file);
			} else if ("ROMX_LOAD_WORD_SKIP_6".equals(entry.type)) {
				ROMX_LOAD_WORD_SKIP_6(entry.file, entry.location, entry.length, results);
				System.out.println("Read romx word skip 6 file " + entry.file);
			} else if ("ROM_LOAD64_BYTE".equals(entry.type)) {
				ROM_LOAD64_BYTE(entry.file, entry.location, entry.length, results);
				System.out.println("Read romx word skip 6 file " + entry.file);
			} else if ("ROM_LOAD".equals(entry.type)) {
				ROM_LOAD(entry.file, entry.location, entry.length, results);
				System.out.println("Read rom file " + entry.file);
			} if ("ROM_LOAD16_BYTE_SWAP".equals(entry.type)) {
				ROM_LOAD_16_BYTE_SWAP(entry.file, entry.location, entry.length, results);
				System.out.println("Read rom 16 byte swap file " + entry.file);
			} else {
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
	
	private static List<ConfigEntry> loadConfig(String configFile) {
		List<ConfigEntry> entries = new ArrayList<ConfigEntry>();
		File file = new File(configFile);
		try {
			FileReader reader = new FileReader(file);
			BufferedReader buff = new BufferedReader(reader);
			String read = buff.readLine();
			while (read != null) {
				String[] split = read.split(",");
				entries.add(new ConfigEntry(split[0].trim(), split[1].trim(), Integer.valueOf(split[2].trim(), 16), Integer.valueOf(split[3].trim(), 16)));
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
			if ("ROM_LOAD16_BYTE".equals(entry.type)) {
				size = entry.length * 2 + entry.location;
			} else if ("ROM_LOAD32_BYTE".equals(entry.type)) {
				size = entry.length * 4 + entry.location;
			} else if ("ROM_LOAD16_WORD_SWAP".equals(entry.type)) {
				size = entry.length + entry.location;
			} else if ("ROMX_LOAD_WORD_SKIP_6".equals(entry.type)) {
				size = entry.length * 4 + ((entry.location >> 4) << 4);
			} else if ("ROM_LOAD64_BYTE".equals(entry.type)) {
				size = entry.length * 8 + ((entry.location >> 4) << 4);
			} else if ("ROM_LOAD".equals(entry.type)) {
				size = entry.length + entry.location;
			} else if ("ROM_LOAD16_BYTE_SWAP".equals(entry.type)) {
				size = entry.length * 2 + entry.location;
			} else {
				throw new RuntimeException("Invalid entry type in split config. " + entry.type);
			}
			if (size > max) {
				max = size;
			}
		}
		return max;
	}
	
	private static class ConfigEntry {
		public String type;
		public String file;
		public int location;
		public int length;
		
		public ConfigEntry(String type, String file, int location, int length) {
			this.type = type;
			this.file = file;
			this.location = location;
			this.length = length;
		}
	}
}
