package io.openems.edge.socketcan.driver;

//Taken from: https://github.com/entropia/libsocket-can-java

import java.io.Closeable;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

public final class CanSocket implements Closeable {
	static {
		boolean libraryLoaded = StaticLibLoaderUtils.loadLibraryFromJarWithOSDetection("/lib", "jni_socketcan");
		if (libraryLoaded) {
			System.err.println("Succesfully loaded native linux library from JAR.");
		} else {
			System.err.println("Not able to find and load native library.");
		}
	}

	private static void copyStream(final InputStream in, final OutputStream out) throws IOException {
		final int BYTE_BUFFER_SIZE = 0x1000;
		final byte[] buffer = new byte[BYTE_BUFFER_SIZE];
		for (int len; (len = in.read(buffer)) != -1;) {
			out.write(buffer, 0, len);
		}
	}

	private static void loadLibFromJar(final String libName) throws IOException {
		Objects.requireNonNull(libName);
		final String fileName = "/lib/lib" + libName + ".so";
		System.out.println("Load from JAR: " + fileName);
		final FileAttribute<Set<PosixFilePermission>> permissions = PosixFilePermissions
				.asFileAttribute(PosixFilePermissions.fromString("rw-------"));
		final Path tempSo = Files.createTempFile(CanSocket.class.getName(), ".so", permissions);
		try {
			try (final InputStream libstream = CanSocket.class.getResourceAsStream(fileName)) {
				if (libstream == null) {
					throw new FileNotFoundException("jar:*!" + fileName);
				}
				try (final OutputStream fout = Files.newOutputStream(tempSo, StandardOpenOption.WRITE,
						StandardOpenOption.TRUNCATE_EXISTING)) {
					copyStream(libstream, fout);
				}
			}
			System.out.println("Unpacking succesful.\nStarting loading from " + tempSo.toAbsolutePath());
			System.load(tempSo.toString());
			System.out.println("Loading succesful. Now init.");
			initCanLibrary();
			System.err.println("Load from JAR done ---------");
		} catch(Exception e) {
			System.out.println("Failed initCanLibrary();");
		} finally {
			System.out.println("");
			Files.delete(tempSo);
		}
	}

	private final static char[] hexArray = "0123456789ABCDEF".toCharArray();

	public static String bytesToHex(byte[] bytes) {
		char[] hexChars = new char[bytes.length * 2];
		for (int j = 0; j < bytes.length; j++) {
			int v = bytes[j] & 0xFF;
			hexChars[j * 2] = hexArray[v >>> 4];
			hexChars[j * 2 + 1] = hexArray[v & 0x0F];
		}
		return new String(hexChars);
	}

	public static final CanInterface CAN_ALL_INTERFACES = new CanInterface(0);

	private static native void initCanLibrary();
	
	private static native int _getCANID_SFF(final int canid);

	private static native int _getCANID_EFF(final int canid);

	private static native int _getCANID_ERR(final int canid);

	private static native boolean _isSetEFFSFF(final int canid);

	private static native boolean _isSetRTR(final int canid);

	private static native boolean _isSetERR(final int canid);

	private static native int _setEFFSFF(final int canid);

	private static native int _setRTR(final int canid);

	private static native int _setERR(final int canid);

	private static native int _clearEFFSFF(final int canid);

	private static native int _clearRTR(final int canid);

	private static native int _clearERR(final int canid);

	private static native int _openSocketRAW() throws IOException;

	private static native int _openSocketBCM() throws IOException;

	private static native void _close(final int fd) throws IOException;

	private static native int _fetchInterfaceMtu(final int fd, final String ifName) throws IOException;

	private static native int _fetch_CAN_MTU();

	private static native int _fetch_CAN_FD_MTU();

	private static native int _discoverInterfaceIndex(final int fd, final String ifName) throws IOException;

	private static native String _discoverInterfaceName(final int fd, final int ifIndex) throws IOException;

	private static native void _bindToSocket(final int fd, final int ifId) throws IOException;

	private static native CanFrame _recvFrame(final int fd) throws IOException;

	private static native void _sendFrame(final int fd, final int canif, final int canid, final byte[] data)
			throws IOException;

	private static native void _sendCyclicallyAdd(final int fd, final int canif, final int canid, final byte[] data, final int cycleTime)
			throws IOException;

	private static native void _sendCyclicallyRemove(final int fd, final int canif, final int canid, final byte[] data)
			throws IOException;

	private static native void _removeCyclicalAll(final int fd) throws IOException;

	private static native void _sendCyclicallyAdopt(final int fd, final int canif, final int canid, final byte[] data)
			throws IOException;

	private static native void _enableCyclicallyAutoIncrement(final int fd, final int canAddress, final int autoIncrementByteIndex) 
			throws IOException;
			
	private static native int _statsGetCanFrameErrorCntrCyclicalSend(final int fd)
			throws IOException;
	
	private static native int _statsGetCanFrameErrorCntrSend(final int fd)
			throws IOException;
	
	private static native int _statsGetCanFrameErrorCntrReceive(final int fd)
			throws IOException;
	
	private static native int _statsGetCanFrameFramesSendPerCycle(final int fd)
			throws IOException;


	public static final int CAN_MTU = _fetch_CAN_MTU();
	public static final int CAN_FD_MTU = _fetch_CAN_FD_MTU();

	private static native int _fetch_CAN_RAW_FILTER();

	private static native int _fetch_CAN_RAW_ERR_FILTER();

	private static native int _fetch_CAN_RAW_LOOPBACK();

	private static native int _fetch_CAN_RAW_RECV_OWN_MSGS();

	private static native int _fetch_CAN_RAW_FD_FRAMES();

	private static native int _setFilters(final int fd, String data);

	private static native byte[] _getFilters(final int fd);




	/**
	 * @param data contains one filter object. Convenience method to deal with
	 *             situations where exactly one filter is needed.
	 */
	public void setFilters(CanFilter data) {
		CanFilter[] filterArray = { data };
		setFilters(filterArray);

	}

	/**
	 * @param data contains an array of CanFilter objects. The method will loop
	 *             through all of them and set the appropriate filter on the CanBus
	 *             socket.
	 */
	public void setFilters(CanFilter[] data)
	// public void setFilters(Object[] data)

	{
		String filterString = ""; // set the default to accept everything
		// method as a comma-separated String of filter definitions (canid:canmask)
		// The native method expects a filter definition in the following form (HEX!):
		// "0x12345678:0xDFFFFFFF"
		// First, let's reset the filters:
		int numfilter = 0;
		int filtercounter = data.length - 1;
		if (CanSocket._setFilters(_fd, "0x00000000:0x00000000") == -1)
			System.out.println("Unable to reset filter");
		for (CanFilter f : data) {
			filterString += f.getIdHex() + ":" + f.getMaskHex();
			if (numfilter < filtercounter)
				filterString += ",";
			numfilter++;

			System.out.println("filterData is: " + filterString);
		}

		if (CanSocket._setFilters(_fd, filterString) == -1)
			System.out.println("Filter errors");
	}

	static int bitExtract(int number, int k, int p) {
		return (((1 << k) - 1) & (number >> (p - 1)));
	}

	static byte[] reverseBytes(byte[] inArray) {
		for (int left = 0, right = inArray.length - 1; left < right; ++left, --right) {
			byte temp = inArray[left];
			inArray[left] = inArray[right];
			inArray[right] = temp;
		}
		return inArray;
	}

	public void getFilters() {
		int FILTER_SIZE = 8;
		int numFilter = 0;
		byte[] filterData = CanSocket._getFilters(_fd);

		if (filterData != null)
			numFilter = filterData.length / FILTER_SIZE;

		System.out.println("I have found " + numFilter + " filter(s).");
		System.out.println("Full filter in HEX: " + CanSocket.bytesToHex(filterData));

		for (int i = 0; i < numFilter; i++) {
			int startingPos = i * 8;
			int endPos = i * 8 + 4;
			System.out.println("Looping " + i + 1 + " out of " + numFilter + ", startingPos, endPos=" + startingPos
					+ ", " + endPos);
			byte[] canid = reverseBytes(Arrays.copyOfRange(filterData, startingPos, startingPos + 4));
			byte[] mask = reverseBytes(Arrays.copyOfRange(filterData, endPos, endPos + 4));
			ByteBuffer bb = ByteBuffer.wrap(canid);
			int cannumber = bb.getInt();
			// int CAN_EFF_MASK = 0b11111111111111111111111111111; //29 bits
			// int can2 = cannumber & CAN_EFF_MASK;
			ByteBuffer bbm = ByteBuffer.wrap(mask);
			int intMask = bbm.getInt();
			intMask = ~intMask;
			long unsignedMask = intMask;
			unsignedMask = unsignedMask & 0xffffffff;
			cannumber = CanSocket.bitExtract(cannumber, 29, 1);
			System.out.println("The 29 bits are: " + cannumber);
			System.out.println("The mask is: " + unsignedMask);

		}

	}

	private static final int CAN_RAW_FILTER = _fetch_CAN_RAW_FILTER();

	/**
	 * @return the canRawFilter
	 */
	public static int getCanRawFilter() {
		return CAN_RAW_FILTER;
	}

	private static final int CAN_RAW_ERR_FILTER = _fetch_CAN_RAW_ERR_FILTER();
	private static final int CAN_RAW_LOOPBACK = _fetch_CAN_RAW_LOOPBACK();
	private static final int CAN_RAW_RECV_OWN_MSGS = _fetch_CAN_RAW_RECV_OWN_MSGS();
	private static final int CAN_RAW_FD_FRAMES = _fetch_CAN_RAW_FD_FRAMES();

	private static native void _setsockopt(final int fd, final int op, final int stat) throws IOException;

	private static native int _getsockopt(final int fd, final int op) throws IOException;

	private static native void _setreceivetimeout(final int fd, final int secs, final int usecs) throws IOException;


	/**
	 * @author awaal
	 *
	 */
	public final static class CanId implements Cloneable {
		private int _canId = 0;

		public static enum StatusBits {
			ERR, EFFSFF, RTR
		}

		/**
		 * @param intAddress should be an integer representation of the can-id. If you
		 *                   have a hex representation, prefix it with 0x to have the
		 *                   JVM perform the cast to int, if you insist on using a
		 *                   string representation, you can also use the overloaded
		 *                   constructor
		 */
		public CanId(final int intAddress) {
			_canId = intAddress;
		}

		/**
		 * @param strAddress can be a HEX string for the can-id. If you have an int (or
		 *                   a 0x prefixed int), please use the overloaded constructor
		 */
		public CanId(final String strAddress) {
			try {
				_canId = Integer.parseInt(strAddress);
			} catch (NumberFormatException e) {
				// cannot parse the address, not a proper hex representation?
				System.out.println("CanId - constructor: Unable to parse " + strAddress + " to a int. Is it HEX?");
				_canId = 0;
			}
		}

		public boolean isSetEFFSFF() {
			return _isSetEFFSFF(_canId);
		}

		public boolean isSetRTR() {
			return _isSetRTR(_canId);
		}

		public boolean isSetERR() {
			return _isSetERR(_canId);
		}

		public CanId setEFFSFF() {
			_canId = _setEFFSFF(_canId);
			return this;
		}

		public CanId setRTR() {
			_canId = _setRTR(_canId);
			return this;
		}

		public CanId setERR() {
			_canId = _setERR(_canId);
			return this;
		}

		public CanId clearEFFSFF() {
			_canId = _clearEFFSFF(_canId);
			return this;
		}

		public CanId clearRTR() {
			_canId = _clearRTR(_canId);
			return this;
		}

		public CanId clearERR() {
			_canId = _clearERR(_canId);
			return this;
		}

		/**
		 * @return the int representation of the can-id. Use getCanIdHEX to get the HEX
		 *         representation
		 */
		public int getCanId() {
			return _getCANID_EFF(_canId);
		}

		public int getCanId_ERR() {
			return _getCANID_ERR(_canId);
		}

		/**
		 * @return This method returns the can-id in HEX representation
		 */
		public String getCanId_SFFHex() {
			// return the "small" can-id:
			// Do not touch the member variable
			int canId = _getCANID_SFF(_canId); // & CAN_SFF_MASK;
			// log.debug("canId " + canId);
			if (canId > 2047) {
				// log.warn("CanId too big for Standard CANID. Returning max (0x7FF)");
				canId = 0x7FF;
			}
			// _canId&=CAN_SFF_MASK;
			return String.format("%03X", canId);
			// return padToLength(Integer.toString(canId), SFF_LENGTH);
		}

		/**
		 * @return This method returns the can-id in HEX representation
		 */
		public String getCanId_EFFHex() {

			return String.format("0x%08X", _getCANID_EFF(_canId));

		}

		public int getCanId_SFF() {
			// return the "small" can-id:
			// Do not touch the member variable
			int canId = _getCANID_SFF(_canId); // & CAN_SFF_MASK;
			// log.debug("canId " + canId);
			if (canId > 2047) {
				// log.warn("CanId too big for Standard CANID. Returning max (0x7FF)");
				canId = 0x7FF;
			}
			return canId;
		}

		public int getCanId_EFF() {

			return _getCANID_EFF(_canId);
		}

		@Override
		protected Object clone() {
			return new CanId(_canId);
		}

		private Set<StatusBits> _inferStatusBits() {
			final EnumSet<StatusBits> bits = EnumSet.noneOf(StatusBits.class);
			if (isSetERR()) {
				bits.add(StatusBits.ERR);
			}
			if (isSetEFFSFF()) {
				bits.add(StatusBits.EFFSFF);
			}
			if (isSetRTR()) {
				bits.add(StatusBits.RTR);
			}
			return Collections.unmodifiableSet(bits);
		}

		@Override
		public String toString() {
			return "CanId [canId=" + (isSetEFFSFF() ? getCanId_EFF() : getCanId_SFF()) + "flags=" + _inferStatusBits()
					+ "]";
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + _canId;
			return result;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			CanId other = (CanId) obj;
			if (_canId != other._canId)
				return false;
			return true;
		}
	}

	/**
	 * @author awaal This class prepares a read filter for the canbus. It takes a
	 *         CanId to represent the can-id, and a filter mask (4 bytes) to
	 *         represent the filter.
	 */
	public static final class CanFilter {

		/**
		 * This bit inverts the filter.
		 */
		public static final int ERR_FLAG = 0b00100000000000000000000000000000;
		public static final int INVERTED_BIT = ERR_FLAG;

		/**
		 * This predefined filter accepts any CAN ID.
		 */
		public static final CanFilter ANY = new CanFilter(new CanId(0), 0);

		/**
		 * This predefined filter accepts no CAN ID at all.
		 */
		public static final CanFilter NONE = new CanFilter(new CanId(0));

		/**
		 * This filter mask can be used to match a CAN ID exactly.
		 */
		public static final int EXACT = 0xDFFFFFFF;
		public static final int ALL = 0x0;

		private CanId id;
		private int mask;
		// private final String mask;

		/**
		 * Creates a filter to exactly matches the given ID.
		 *
		 * @param id the CAN ID to match
		 */
		public CanFilter(CanId id) {
			this(id, EXACT);
		}

		/**
		 * Creates a filter with the given CAN ID and mask.
		 *
		 * @param id      The CAN ID to match
		 * @param lngMask the mask to match
		 */
		public CanFilter(CanId id, int intMask) {
			this.id = id;
			this.mask = intMask;
		}

		/**
		 * Creates a filter with the given CAN ID and mask. Overloaded to support
		 * feeding it a String Should the conversion fail, the mask will pass ALL
		 *
		 * @param id      The CAN ID to match
		 * @param strMask the mask to match
		 */
		public CanFilter(CanId id, String strMask) {
			this.id = id;
			try {
				this.mask = Integer.parseInt(strMask);
			} catch (NumberFormatException e) {
				System.out.println("Cannot parse " + strMask + " to int. Is it a HEX representation?");
				this.mask = ALL;
			}
		}

		/**
		 * Gets the CAN ID to be matched by this filter.
		 *
		 * @return the CAN ID
		 */
		public int getId() {
			return id.getCanId();
		}

		public String getIdHex() {
			return id.getCanId_EFFHex();
		}

		/**
		 * Gets the mask to used to match the CAN ID.
		 *
		 * @return the mask
		 */
		public String getMaskHex() {
			return String.format("0x%08X", mask);
		}

		public long getMask() {
			return mask;
		}

		/**
		 * Checks if this filter is inverted.
		 *
		 * @return true if this filter is inverted
		 */
		public boolean isInverted() {
			return (id.getCanId() & INVERTED_BIT) > 0;
		}

		/**
		 * Checks if this filter matches its CAN ID exactly.
		 *
		 * @return true if this filter is exact
		 */
		public boolean isExact() {
			return mask == EXACT;
		}

		/**
		 * Matches this filter against the given CAN ID. This method is implemented
		 * exactly like the kernel implements the filtering.
		 *
		 * @param id the CAN ID to match
		 * @return true if the given CAN ID would be accepted by this filter
		 */
		public boolean matchId(int id) {

			return (this.id.getCanId() & mask) == (id & mask);
		}

		@Override
		public boolean equals(Object o) {
			if (this == o)
				return true;
			if (o == null || getClass() != o.getClass())
				return false;
			CanFilter canFilter = (CanFilter) o;
			return id == canFilter.id && mask == canFilter.mask;
		}

		@Override
		public int hashCode() {
			return Objects.hash(id, mask);
		}

		@Override
		public String toString() {
			return (isInverted() ? "~" : "") + String.format("CanFilter(id=%X, mask=%X)", id, mask);
		}

	}

	public final static class CanInterface implements Cloneable {
		private final int _ifIndex;
		private String _ifName;

		public CanInterface(final CanSocket socket, final String ifName) throws IOException {
			this._ifIndex = _discoverInterfaceIndex(socket._fd, ifName);
			this._ifName = ifName;
		}

		private CanInterface(int ifIndex, String ifName) {
			this._ifIndex = ifIndex;
			this._ifName = ifName;
		}

		private CanInterface(int ifIndex) {
			this(ifIndex, null);
		}

		public int getInterfaceIndex() {
			return _ifIndex;
		}

		@Override
		public String toString() {
			return "CanInterface [_ifIndex=" + _ifIndex + ", _ifName=" + _ifName + "]";
		}

		public String getIfName() {
			return _ifName;
		}

		public String resolveIfName(final CanSocket socket) {
			if (_ifName == null) {
				try {
					_ifName = _discoverInterfaceName(socket._fd, _ifIndex);
				} catch (IOException e) {
					/* EMPTY */ }
			}
			return _ifName;
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + _ifIndex;
			result = prime * result + ((_ifName == null) ? 0 : _ifName.hashCode());
			return result;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			CanInterface other = (CanInterface) obj;
			if (_ifIndex != other._ifIndex)
				return false;
			if (_ifName == null) {
				if (other._ifName != null)
					return false;
			} else if (!_ifName.equals(other._ifName))
				return false;
			return true;
		}

		@Override
		protected Object clone() {
			return new CanInterface(_ifIndex, _ifName);
		}
	}

	public final static class CanFrame implements Cloneable {
		private final CanInterface canIf;
		private final CanId canId;
		private final byte[] data;

		public CanFrame(final CanInterface canIf, final CanId canId, byte[] data) {
			this.canIf = canIf;
			this.canId = canId;
			this.data = data;
		}

		/* this constructor is used in native code */
		@SuppressWarnings("unused")
		private CanFrame(int canIf, int canid, byte[] data) {
			if (data.length > 8) {
				throw new IllegalArgumentException();
			}
			this.canIf = new CanInterface(canIf);
			this.canId = new CanId(canid);
			this.data = data;
		}

		public CanId getCanId() {
			return canId;
		}

		public byte[] getData() {
			return data;
		}

		public CanInterface getCanInterfacae() {
			return canIf;
		}

		@Override
		public String toString() {
			return "CanFrame [canIf=" + canIf + ", canId=" + canId + ", data=" + Arrays.toString(data) + "]";
		}

		@Override
		protected Object clone() {
			return new CanFrame(canIf, (CanId) canId.clone(), Arrays.copyOf(data, data.length));
		}
	}

	public static enum Mode {
		RAW, BCM
	}

	private int _fd;
	private final Mode _mode;
	private CanInterface _boundTo;

	public CanSocket(Mode mode) { // throws IOException {
		switch (mode) {
		case BCM:
			try {
				_fd = _openSocketBCM();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			break;
		case RAW:
			try {
				_fd = _openSocketRAW();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			break;
		default:
			break;
		// throw new IllegalStateException("unkown mode " + mode);
		}
		this._mode = mode;
	}

	public void bind(CanInterface canInterface) throws IOException {
		_bindToSocket(_fd, canInterface._ifIndex);
		this._boundTo = canInterface;
	}

	public void send(CanFrame frame) throws IOException {
		_sendFrame(_fd, frame.canIf._ifIndex, frame.canId._canId, frame.data);
	}

	public CanFrame recv() throws IOException {
		return _recvFrame(_fd);
	}

	@Override
	public void close() throws IOException {
		_close(_fd);
	}

	/**
	 * sets the sockets read timeout, if 0 the socket returns from NON-BLOCKING to BLOCKING
	 * @param _secs
	 * @param _usecs
	 * @throws IOException
	 *
	 */
	public void setReceiveTimeout(int _secs, int _usecs) throws IOException {
		_setreceivetimeout(_fd, _secs, _usecs);
	}

	public void setSocketOptions(int _stat) throws IOException {
		_setsockopt(_fd, CAN_RAW_FILTER, _stat);
	}

	public int getMtu(final String canif) throws IOException {
		return _fetchInterfaceMtu(_fd, canif);
	}

	public void setLoopbackMode(final boolean on) throws IOException {
		_setsockopt(_fd, CAN_RAW_LOOPBACK, on ? 1 : 0);
	}

	public boolean getLoopbackMode() throws IOException {
		return _getsockopt(_fd, CAN_RAW_LOOPBACK) == 1;
	}

	public void setRecvOwnMsgsMode(final boolean on) throws IOException {
		_setsockopt(_fd, CAN_RAW_RECV_OWN_MSGS, on ? 1 : 0);
	}

	public boolean getRecvOwnMsgsMode() throws IOException {
		return _getsockopt(_fd, CAN_RAW_RECV_OWN_MSGS) == 1;
	}




	//enhanced interface to start cyclically CAN send mechanism within the C driver

	/**
	 * @brief adds the given frame to the native cyclical send task
	 * @param frame
	 * @param cycleTime in ms 
	 * @throws IOException
	 * 
	 * @Note library supports only one cycle time, therefore all function calls must use the same cycle time
	 */
	public void sendCyclicallyAdd(CanFrame frame, int cycleTime) throws IOException{
		_sendCyclicallyAdd(_fd, frame.canIf._ifIndex, frame.canId._canId, frame.data, cycleTime);
	}

	/**
	 * @brief removes the given frame from the native cyclical send task
	 * @param frame
	 * @throws IOException
	 */
	public void sendCyclicallyRemove(CanFrame frame) throws IOException{
		_sendCyclicallyRemove(_fd, frame.canIf._ifIndex, frame.canId._canId, frame.data);
	}


	/**
	 * @brief adopts the data of the frame already handled by the native cyclical send task
	 * @param frame
	 * @throws IOException
	 */
	public void sendCyclicallyAdopt(CanFrame frame) throws IOException{
		_sendCyclicallyAdopt(_fd, frame.canIf._ifIndex, frame.canId._canId, frame.data);
	}


	/**
	 * @brief removes the given frame from the native cyclical send task
	 * @param frame
	 * @throws IOException
	 */
	public void removeCyclicalAll() throws IOException{
		_removeCyclicalAll(_fd);
	}

	/**
	 * @brief enables the auto increment option for the given canAddress and the byte at the given position.
	 * 
	 * If there is a CAN frame with the given address, then this byte will automatically be incremented
	 * @param canAddress the address of the CAN frame to use for autoincrement
	 * @param autoIncrementByteIndex the data position 
	 * @throws IOException
	 * @note for some kinds of battery management systems java is to slow to automatically increment the counter (e.g. every 100ms).
	 * This low lewel method can handle this more time critical
	 */
	public void enableCyclicallyAutoIncrement(int canAddress, int autoIncrementByteIndex) throws IOException{
		_enableCyclicallyAutoIncrement(_fd, canAddress, autoIncrementByteIndex); 
	}
	
	/**
	 * @brief gets the error counter for error on cyclical send can frames. 
	 * @throws IOException
	 */
	public int statsGetCanFrameErrorCntrCyclicalSend() throws IOException{
		return _statsGetCanFrameErrorCntrCyclicalSend(_fd);
	}

	/**
	 * @brief gets the error counter for errors on regular send can frames. 
	 * @throws IOException
	 */
	public int statsGetCanFrameErrorCntrSend() throws IOException{
		return _statsGetCanFrameErrorCntrSend(_fd);
	}

	/**
	 * @brief gets the error counter for errors on regular received can frames. 
	 * @throws IOException
	 */
	public int statsGetCanFrameErrorCntrReceive() throws IOException{
		return _statsGetCanFrameErrorCntrReceive(_fd);
	}

	/**
	 * @brief gets the framesSendPerCycle counter for cyclical can frames. 
	 * @throws IOException
	 */
	public int statsGetCanFrameFramesSendPerCycle() throws IOException{
		return _statsGetCanFrameFramesSendPerCycle(_fd);
	}

	
	
}
