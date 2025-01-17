#include <string>
#include <algorithm>
#include <utility>

#include <cstring>
#include <cstddef>
#include <cerrno>
#include <syslog.h>

#include <sstream>
#include <string.h>

#include <vector>
#include <climits>
#include <memory>

extern "C" {
#include <sys/types.h>
#include <sys/socket.h>
#include <sys/ioctl.h>
#include <unistd.h>

#include <net/if.h>
#include <linux/can.h>
#include <linux/can/raw.h>

}

//#ifdef (ANDROID) || defined(__ANDROID__)
//#include "jni.h"
//#else
#include "io_openems_edge_socketcan_driver_CanSocket.h"
//#endif

#include "cansocket.hpp"


typedef unsigned char BYTE;

static int statsErrorCntrSend = 0;
static int statsErrorCntrReceive = 0;

static jint newCanSocket(JNIEnv *env, int socket_type, int protocol) {
	const int fd = socket(PF_CAN, socket_type, protocol);
	if (fd != -1) {
		return fd;
	}
	throwIOExceptionErrno(env, errno);
	return -1;
}

JNIEXPORT void JNICALL Java_io_openems_edge_socketcan_driver_CanSocket_initCanLibrary(JNIEnv *env, jclass obj) {
	cyclicalInitLowLevelThread();
	if(CAN_NPROTO == 8){
		fprintf(stderr, "CAN Lib: Init lowlevel thread done for kernel 5.1\n");
	}else{
		fprintf(stderr, "CAN Lib: Init lowlevel thread done for kernel 4.1\n");
	}

}

JNIEXPORT jint JNICALL Java_io_openems_edge_socketcan_driver_CanSocket__1openSocketRAW(
		JNIEnv *env, jclass obj) {
	return newCanSocket(env, SOCK_RAW, CAN_RAW);
}

JNIEXPORT jint JNICALL Java_io_openems_edge_socketcan_driver_CanSocket__1openSocketBCM(
		JNIEnv *env, jclass obj) {
	return newCanSocket(env, SOCK_DGRAM, CAN_BCM);
}

JNIEXPORT void JNICALL Java_io_openems_edge_socketcan_driver_CanSocket__1close
(JNIEnv *env, jclass obj, jint fd)
{
	if (close(fd) == -1) {
		throwIOExceptionErrno(env, errno);
	}
}

JNIEXPORT jint JNICALL Java_io_openems_edge_socketcan_driver_CanSocket__1discoverInterfaceIndex(
		JNIEnv *env, jclass clazz, jint socketFd, jstring ifName) {
	struct ifreq ifreq;
	const jsize ifNameSize = env->GetStringUTFLength(ifName);
	if (ifNameSize > IFNAMSIZ - 1 || ifNameSize < 1) {
		throwIllegalArgumentException(env, "illegal interface name");
		return -1;
	}

	/* fetch interface name */
	memset(&ifreq, 0x0, sizeof(ifreq));
	env->GetStringUTFRegion(ifName, 0, ifNameSize, ifreq.ifr_name);
	if (env->ExceptionCheck() == JNI_TRUE) {
		printf("Error while getting interface name from java.");
		return -1;
	}
	/* discover interface id */
	const int err = ioctl(socketFd, SIOCGIFINDEX, &ifreq);
	if (err == -1) {
		throwIOExceptionErrno(env, errno);
		return -1;
	} else {
		return ifreq.ifr_ifindex;
	}
}

JNIEXPORT jstring JNICALL Java_io_openems_edge_socketcan_driver_CanSocket__1discoverInterfaceName(
		JNIEnv *env, jclass obj, jint fd, jint ifIdx) {
	struct ifreq ifreq;
	memset(&ifreq, 0x0, sizeof(ifreq));
	ifreq.ifr_ifindex = ifIdx;
	if (ioctl(fd, SIOCGIFNAME, &ifreq) == -1) {
		throwIOExceptionErrno(env, errno);
		return NULL;
	}
	const jstring ifname = env->NewStringUTF(ifreq.ifr_name);
	return ifname;
}

JNIEXPORT void JNICALL Java_io_openems_edge_socketcan_driver_CanSocket__1bindToSocket
(JNIEnv *env, jclass obj, jint fd, jint ifIndex)
{
	struct sockaddr_can addr;
	addr.can_family = AF_CAN;
	addr.can_ifindex = ifIndex;
	if (bind(fd, reinterpret_cast<struct sockaddr *>(&addr), sizeof(addr)) != 0) {
		throwIOExceptionErrno(env, errno);
	}
}

JNIEXPORT void JNICALL Java_io_openems_edge_socketcan_driver_CanSocket__1sendFrame
(JNIEnv *env, jclass obj, jint fd, jint if_idx, jint canid, jbyteArray data)
{
	const int flags = 0;
	ssize_t nbytes;
	struct sockaddr_can addr;
	struct can_frame frame;
	memset(&addr, 0, sizeof(addr));
	memset(&frame, 0, sizeof(frame));
	addr.can_family = AF_CAN;
	addr.can_ifindex = if_idx;
	const jsize len = env->GetArrayLength(data);
	if (env->ExceptionCheck() == JNI_TRUE) {
		return;
	}
	frame.can_id = canid;
	frame.can_dlc = static_cast<__u8>(len);
	env->GetByteArrayRegion(data, 0, len, reinterpret_cast<jbyte *>(&frame.data));
	if (env->ExceptionCheck() == JNI_TRUE) {
		return;
	}
	nbytes = sendto(fd, &frame, sizeof(frame), flags,
			reinterpret_cast<struct sockaddr *>(&addr),
			sizeof(addr));
	if (nbytes == -1) {
		statsErrorCntrSend++;
		throwIOExceptionErrno(env, errno);
	} else if (nbytes != sizeof(frame)) {
		statsErrorCntrSend++;
		throwIOExceptionMsg(env, "send partial frame");
	}
}

JNIEXPORT jobject JNICALL Java_io_openems_edge_socketcan_driver_CanSocket__1recvFrame(
		JNIEnv *env, jclass obj, jint fd) {
	//const int flags = 0;
	const int flags = MSG_WAITALL;
	ssize_t nbytes;
	struct sockaddr_can addr;
	socklen_t len = sizeof(addr);
	struct can_frame frame;
	memset(&addr, 0, sizeof(addr));
	memset(&frame, 0, sizeof(frame));
	nbytes = recvfrom(fd, &frame, sizeof(frame), flags,
			reinterpret_cast<struct sockaddr *>(&addr), &len);

	if(   (CAN_NPROTO == 8 && len !=           8 ) //note: linux kernel 5.1:  len ==> 8, probably due to old CAN library support in kunbus connect S
	   || (CAN_NPROTO == 7 && len != sizeof(addr)) //note: linux kernel 4.19: len ==> sizeof(addr), which is 8 on kunbus connect plus
					){
		statsErrorCntrReceive++;
		throwIllegalArgumentException(env, "illegal AF_CAN address");
		return NULL;
	}
	if (nbytes == -1) {
		throwIOExceptionErrno(env, errno);
		return NULL;
	} else if (nbytes != sizeof(frame)) {
		statsErrorCntrReceive++;
		throwIOExceptionMsg(env, "invalid length of received frame");
		return NULL;
	}
	const jsize fsize = static_cast<jsize>(std::min(
			static_cast<size_t>(frame.can_dlc),
			static_cast<size_t>(nbytes - offsetof(struct can_frame, data))));
	const jclass can_frame_clazz = env->FindClass("io/openems/edge/socketcan/driver/"
			"CanSocket$CanFrame");
	if (can_frame_clazz == NULL) {
		return NULL;
	}
	const jmethodID can_frame_cstr = env->GetMethodID(can_frame_clazz, "<init>",
			"(II[B)V");
	if (can_frame_cstr == NULL) {
		return NULL;
	}
	const jbyteArray data = env->NewByteArray(fsize);
	if (data == NULL) {
		if (env->ExceptionCheck() != JNI_TRUE) {
			throwOutOfMemoryError(env, "could not allocate ByteArray");
		}
		return NULL;
	}
	env->SetByteArrayRegion(data, 0, fsize,
			reinterpret_cast<jbyte *>(&frame.data));
	if (env->ExceptionCheck() == JNI_TRUE) {
		return NULL;
	}
	const jobject ret = env->NewObject(can_frame_clazz, can_frame_cstr,
			addr.can_ifindex, frame.can_id, data);
	return ret;
}

JNIEXPORT jint JNICALL Java_io_openems_edge_socketcan_driver_CanSocket__1fetchInterfaceMtu(
		JNIEnv *env, jclass obj, jint fd, jstring ifName) {
	struct ifreq ifreq;

	const jsize ifNameSize = env->GetStringUTFLength(ifName);
	if (ifNameSize > IFNAMSIZ - 1) {
		throwIllegalArgumentException(env, "illegal interface name");
		return -1;
	}
	memset(&ifreq, 0x0, sizeof(ifreq));
	env->GetStringUTFRegion(ifName, 0, ifNameSize, ifreq.ifr_name);
	if (env->ExceptionCheck() == JNI_TRUE) {
		return -1;
	}
	if (ioctl(fd, SIOCGIFMTU, &ifreq) == -1) {
		throwIOExceptionErrno(env, errno);
		return -1;
	} else {
		return ifreq.ifr_mtu;
	}
}

JNIEXPORT void JNICALL Java_io_openems_edge_socketcan_driver_CanSocket__1setsockopt
(JNIEnv *env, jclass obj, jint fd, jint op, jint stat)
{
	const int _stat = stat;
	if (setsockopt(fd, SOL_CAN_RAW, op, &_stat, sizeof(_stat)) == -1) {
		throwIOExceptionErrno(env, errno);
	}
}


JNIEXPORT void JNICALL Java_io_openems_edge_socketcan_driver_CanSocket__1setreceivetimeout
(JNIEnv *env, jclass obj, jint fd, jint sec, jint usec)
{
	timeval tv;
	tv.tv_sec = sec;
	tv.tv_usec = usec;
	if (setsockopt(fd, SOL_SOCKET, SO_RCVTIMEO, &tv, sizeof(tv)) == -1) {
		throwIOExceptionErrno(env, errno);
	}
}




JNIEXPORT jint JNICALL Java_io_openems_edge_socketcan_driver_CanSocket__1getsockopt(
		JNIEnv *env, jclass obj, jint fd, jint op) {
	int _stat = 0;
	socklen_t len = sizeof(_stat);
	if (getsockopt(fd, SOL_CAN_RAW, op, &_stat, &len) == -1) {
		throwIOExceptionErrno(env, errno);
	}
	if (len != sizeof(_stat)) {
		throwIllegalArgumentException(env,
				"setsockopt return size is different");
		return -1;
	}
	return _stat;
}

/*** constants ***/

JNIEXPORT jint JNICALL Java_io_openems_edge_socketcan_driver_CanSocket__1fetch_1CAN_1MTU(
		JNIEnv *env, jclass obj) {
	return CAN_MTU;
}

JNIEXPORT jint JNICALL Java_io_openems_edge_socketcan_driver_CanSocket__1fetch_1CAN_1FD_1MTU(
		JNIEnv *env, jclass obj) {
	return CANFD_MTU;
}

/*** ioctls ***/
JNIEXPORT jint JNICALL Java_io_openems_edge_socketcan_driver_CanSocket__1fetch_1CAN_1RAW_1FILTER(
		JNIEnv *env, jclass obj) {
	return CAN_RAW_FILTER;
}

JNIEXPORT jint JNICALL Java_io_openems_edge_socketcan_driver_CanSocket__1fetch_1CAN_1RAW_1ERR_1FILTER(
		JNIEnv *env, jclass obj) {
	return CAN_RAW_ERR_FILTER;
}

JNIEXPORT jint JNICALL Java_io_openems_edge_socketcan_driver_CanSocket__1fetch_1CAN_1RAW_1LOOPBACK(
		JNIEnv *env, jclass obj) {
	return CAN_RAW_LOOPBACK;
}

JNIEXPORT jint JNICALL Java_io_openems_edge_socketcan_driver_CanSocket__1fetch_1CAN_1RAW_1RECV_1OWN_1MSGS(
		JNIEnv *env, jclass obj) {
	return CAN_RAW_RECV_OWN_MSGS;
}

JNIEXPORT jint JNICALL Java_io_openems_edge_socketcan_driver_CanSocket__1fetch_1CAN_1RAW_1FD_1FRAMES(
		JNIEnv *env, jclass obj) {
	return CAN_RAW_FD_FRAMES;
}

/*** ADR MANIPULATION FUNCTIONS ***/

JNIEXPORT jint JNICALL Java_io_openems_edge_socketcan_driver_CanSocket__1getCANID_1SFF(
		JNIEnv *env, jclass obj, jint canid) {
	return canid & CAN_SFF_MASK;
}

JNIEXPORT jint JNICALL Java_io_openems_edge_socketcan_driver_CanSocket__1getCANID_1EFF(
		JNIEnv *env, jclass obj, jint canid) {
	return canid & CAN_EFF_MASK;
}

JNIEXPORT jint JNICALL Java_io_openems_edge_socketcan_driver_CanSocket__1getCANID_1ERR(
		JNIEnv *env, jclass obj, jint canid) {
	return canid & CAN_ERR_MASK;
}

JNIEXPORT jboolean JNICALL Java_io_openems_edge_socketcan_driver_CanSocket__1isSetEFFSFF(
		JNIEnv *env, jclass obj, jint canid) {
	return (canid & CAN_EFF_FLAG) != 0 ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL Java_io_openems_edge_socketcan_driver_CanSocket__1isSetRTR(
		JNIEnv *env, jclass obj, jint canid) {
	return (canid & CAN_RTR_FLAG) != 0 ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL Java_io_openems_edge_socketcan_driver_CanSocket__1isSetERR(
		JNIEnv *env, jclass obj, jint canid) {
	return (canid & CAN_ERR_FLAG) != 0 ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jint JNICALL Java_io_openems_edge_socketcan_driver_CanSocket__1setEFFSFF(JNIEnv *env,
		jclass obj, jint canid) {
	return canid | CAN_EFF_FLAG;
}

JNIEXPORT jint JNICALL Java_io_openems_edge_socketcan_driver_CanSocket__1setRTR(JNIEnv *env,
		jclass obj, jint canid) {
	return canid | CAN_RTR_FLAG;
}

JNIEXPORT jint JNICALL Java_io_openems_edge_socketcan_driver_CanSocket__1setERR(JNIEnv *env,
		jclass obj, jint canid) {
	return canid | CAN_ERR_FLAG;
}

JNIEXPORT jint JNICALL Java_io_openems_edge_socketcan_driver_CanSocket__1clearEFFSFF(
		JNIEnv *env, jclass obj, jint canid) {
	return canid & ~CAN_EFF_FLAG;
}

JNIEXPORT jint JNICALL Java_io_openems_edge_socketcan_driver_CanSocket__1clearRTR(JNIEnv *env,
		jclass obj, jint canid) {
	return canid & ~CAN_RTR_FLAG;
}

JNIEXPORT jint JNICALL Java_io_openems_edge_socketcan_driver_CanSocket__1clearERR(JNIEnv *env,
		jclass obj, jint canid) {
	return canid & ~CAN_ERR_FLAG;
}

size_t getCopySize(size_t sourceSize, size_t destSize) {
	return (destSize <= sourceSize ? destSize : sourceSize);
}



JNIEXPORT jbyteArray JNICALL Java_io_openems_edge_socketcan_driver_CanSocket__1getFilters(
		JNIEnv *env, jclass obj, jint sock) {

	std::string s; //for logging purposes

	//attempt 1:
	//socklen_t size = (socklen_t) (sizeof(int) * 4); //Errrm...this is 4 bytes.
	//attempt 2:
	//socklen_t size = INT_MAX;
	//attempt 3:
	// so, a filter definition is 8 bytes, regardless of platform.
	// I am making a design decision here to limit the number of filters to 5
	// so size = 5*8 = 40 bytes
	socklen_t size = 40;

	logthis(s.append("+++++++++++Entering getsockopt"));
	s = "";

	std::unique_ptr<char[]> rfilter(new char[size]);
	// Defer the creation of the out-array until I have a definite on size:
	// std::unique_ptr<char[]> filters_out(new char[size]);

	int result = getsockopt(sock, SOL_CAN_RAW, CAN_RAW_FILTER, rfilter.get(),
			&size);
	if (result == -1) {
		return NULL;
	}
	// The call to getsockopt has now changed the size argument to the proper size
	// of the returned result.
	// See: http://man7.org/linux/man-pages/man2/setsockopt.2.html
	// I am creating a separate variable here for clarity's sake
	socklen_t realsize = size;
	logthis(
			s.append("Size (bytes) of result after getsockopt: ").append(
					std::to_string(realsize)));
	s = "";

	//now, allocate the out-array:
	std::unique_ptr<char[]> filters_out(new char[realsize]);

	//and copy the results:
	memcpy(filters_out.get(), rfilter.get(), realsize);

	jbyteArray data = env->NewByteArray(realsize);

	if (data == NULL) {
		if (env->ExceptionCheck() != JNI_TRUE) {
			throwOutOfMemoryError(env, "could not allocate ByteArray");
		}
		return NULL;
	}

	env->SetByteArrayRegion(data, 0, realsize,
			(jbyte *) filters_out.get());

	return data;
}


JNIEXPORT jint JNICALL Java_io_openems_edge_socketcan_driver_CanSocket__1setFilters(
		JNIEnv *env, jclass obj, jint sock, jstring data) {

	std::string s; //for logging purposes

	int numfilter = 0; //to hold the count (number) of filters
	const char *tempString; // to perform string searches on

	const char *inFilterString = env->GetStringUTFChars(data, NULL);
	if (NULL == inFilterString)
			return -1;

	logthis("====== BEGIN setsockopt");
	// Counting the commas will give us the number of filter definitions
	// as one filter def has one comma.
	tempString = inFilterString;
	std::string noFilter = "0x00000000:0x00000000";
	while (tempString) {
		if ((strlen(inFilterString) != 0) && (inFilterString != noFilter))
			numfilter++;
		tempString++; /* hop behind the ',' */
		tempString = strchr(tempString, ','); /* fails if no more commas are found */
	}

	//Now, numfilter contains the number of filters :)
	if (numfilter == 0)
	{
		logthis("Number of filters is zero; allowing all messages to pass.");
		return setsockopt(sock, SOL_CAN_RAW, CAN_RAW_FILTER, 0, sizeof(0));
	}


	//TODO: This is a VLA, which is not allowed. So, find a way to prevent VLA:
	struct can_filter rfilter[numfilter];
	//attempt 1:
	//typedef can_filter cft;
	//std::vector<cft> rfilter(numfilter);


	// The filter definitions are in the following form: (HEX!):
	//"0x12345678:0xDFFFFFFF"

	tempString = inFilterString; //re-set the tempString to the full input received.
	logthis(s.append("Full filter string (setsockopt): ").append(tempString));
	s = "";


	for (int i = 0; i < numfilter; i++) {
		//cft currentfilter;
		if (i != 0)
			tempString = inFilterString + 1; //only hop after the comma on filters after the first;
		inFilterString = strchr(tempString, ',');
		sscanf(tempString, "%x:%x", &rfilter[i].can_id, &rfilter[i].can_mask);
		rfilter[i].can_id |=  CAN_EFF_FLAG;
		//currentfilter.can_id |=  CAN_EFF_FLAG;
		rfilter[i].can_mask |= (CAN_EFF_FLAG | CAN_RTR_FLAG | CAN_SFF_MASK); // I want it all...
		//currentfilter.can_mask |= (CAN_EFF_FLAG | CAN_RTR_FLAG | CAN_SFF_MASK); // I want it all...
		//rfilter.push_back(currentfilter);
		logthis(
				s.append("setsockopt filter canid[").append(std::to_string(i)).append(
						"]:").append(std::to_string(rfilter[i].can_id)));
		s = "";
		logthis(
				s.append("setsockopt filter mask[").append(std::to_string(i)).append(
						"]:").append(std::to_string(rfilter[i].can_mask)));
		s = "";

		if (inFilterString == NULL)
			logthis("NULL");
	}

	int result = setsockopt(sock, SOL_CAN_RAW, CAN_RAW_FILTER, &rfilter,
			sizeof(rfilter));
	logthis(
			s.append("rfilter size after setsockopt: ").append(
					std::to_string(sizeof(rfilter))));
	s = "";

	return result;
}



JNIEXPORT void JNICALL Java_io_openems_edge_socketcan_driver_CanSocket__1sendCyclicallyAdd
(JNIEnv *env, jclass obj, jint fd, jint if_idx, jint canid, jbyteArray data, jint cycleTime)
{
	const jsize len = env->GetArrayLength(data);
	if (env->ExceptionCheck() == JNI_TRUE) {
		return;
	}
	jbyte buffer[8];
	env->GetByteArrayRegion(data, 0, len, reinterpret_cast<jbyte *>(buffer));
	if (env->ExceptionCheck() == JNI_TRUE) {
		return;
	}
	if(cyclicalTaskAddCanFrame(fd, if_idx, canid, len, buffer, cycleTime )){
		throwIOExceptionMsg(env, "Frame can not be added to cyclial send task");
	}
}



JNIEXPORT void JNICALL Java_io_openems_edge_socketcan_driver_CanSocket__1sendCyclicallyRemove
(JNIEnv *env, jclass obj, jint fd, jint if_idx, jint canid, jbyteArray data)
{
	if(cyclicalTaskRemoveCanFrame(canid)){
		throwIOExceptionMsg(env, "Frame can not be removed from cyclial send task");
	}
}


JNIEXPORT void JNICALL Java_io_openems_edge_socketcan_driver_CanSocket__1removeCyclicalAll
(JNIEnv *env, jclass obj, jint fd)
{
	if(cyclicalTaskRemoveAll()){
		throwIOExceptionMsg(env, "cyclial send task can not be cleared");
	}
}





JNIEXPORT void JNICALL Java_io_openems_edge_socketcan_driver_CanSocket__1sendCyclicallyAdopt
(JNIEnv *env, jclass obj, jint fd, jint if_idx, jint canid, jbyteArray data)
{

	const jsize len = env->GetArrayLength(data);
	if (env->ExceptionCheck() == JNI_TRUE) {
		return;
	}
	jbyte buffer[8];
	env->GetByteArrayRegion(data, 0, len, reinterpret_cast<jbyte *>(buffer));
	if (env->ExceptionCheck() == JNI_TRUE) {
		return;
	}
	if(cyclicalTaskAdoptCanFrame(canid, len, buffer)){
		throwIOExceptionMsg(env, "Frame can not be added to cyclial send task");
	}

}

/** NOTE: this is a proprietary and special method, it provides vendor dependant code and should not be part of this library */
JNIEXPORT void JNICALL Java_io_openems_edge_socketcan_driver_CanSocket__1enableCyclicallyAutoIncrement
  (JNIEnv *env, jclass obj, jint fd, jint canAddress, jint auoIncrementBytePos)
{
	if(cyclicalAutoIncrementAddFunctionality(canAddress, auoIncrementBytePos)){
		throwIOExceptionMsg(env, "CAN address can not be added as cyclical autoincrement mechanism");
	}
}

JNIEXPORT jint JNICALL Java_io_openems_edge_socketcan_driver_CanSocket__1statsGetCanFrameErrorCntrCyclicalSend
  (JNIEnv *env, jclass obj, jint fd)
{
	jint result = statsGetCanFrameErrorCntrCyclicalSend();
	return result;
}

JNIEXPORT jint JNICALL Java_io_openems_edge_socketcan_driver_CanSocket__1statsGetCanFrameErrorCntrSend
	(JNIEnv *env, jclass obj, jint fd)
{
	jint result = statsErrorCntrSend;
	return result;
}

JNIEXPORT jint JNICALL Java_io_openems_edge_socketcan_driver_CanSocket__1statsGetCanFrameErrorCntrReceive
	(JNIEnv *env, jclass obj, jint fd)
{
	jint result = statsErrorCntrReceive;
	return result;
}

JNIEXPORT jint JNICALL Java_io_openems_edge_socketcan_driver_CanSocket__1statsGetCanFrameFramesSendPerCycle
	(JNIEnv *env, jclass obj, jint fd)
{
	jint result = statsGetCanFrameFramesSendPerCycle();
	return result;
}