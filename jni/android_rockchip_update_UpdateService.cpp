/*  --------------------------------------------------------------------------------------------------------
 *  Copyright(C), 2009-2010, Fuzhou Rockchip Co. ,Ltd.  All Rights Reserved.
 *
 *  File:   android_rockchip_update_UpdateService.cpp
 *
 *  Desc:   Java �� UpdateService �� NC.
 *
 *          -----------------------------------------------------------------------------------
 *          < ϰ�� �� ������ > : 
 *
 *              NC :        native code.
 *              HAL :       hardware abstract layer.
 *
 *          -----------------------------------------------------------------------------------
 *          < �ڲ�ģ�� or ����Ĳ�νṹ > :
 *
 *          -----------------------------------------------------------------------------------
 *          < ����ʵ�ֵĻ����� > :
 *
 *          -----------------------------------------------------------------------------------
 *          < �ؼ��ʶ�� > : 
 *
 *          -----------------------------------------------------------------------------------
 *          < ��ģ��ʵ���������ⲿģ�� > : 
 *              ... 
 *          -----------------------------------------------------------------------------------
 *  .! Note:   
 *
 *  Author: ChenZhen
 *
 *  --------------------------------------------------------------------------------------------------------
 *  Version:
 *          v1.0
 *          v1.1 :		Ǩ�Ƶ� android2.1r2 ������, ��ʹ���� frameworks �ⲿʵ�ֵķ�ʽ. 
 *  --------------------------------------------------------------------------------------------------------
 *  Log:
	----Sun Apr 11 16:02:52 2010            v1.0
	----Thu May 13 17:39:52 2010            v1.1
 *
 *  --------------------------------------------------------------------------------------------------------
 */


/* ---------------------------------------------------------------------------------------------------------
 * Include Files
 * ---------------------------------------------------------------------------------------------------------
 */

#define LOG_TAG "android_rockchip_update_UpdateService.cpp"

#include <cutils/log.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <errno.h>
#include <unistd.h>
#include <fcntl.h>
#include <sys/ioctl.h>
#include <assert.h>

#include "jni.h"
#include "nativehelper/JNIHelp.h"

#define LOGE ALOGE
#define LOGD ALOGD
#define LOGI ALOGI
#define UNUSED(...) (void)(__VA_ARGS__)

static const char* const classPathName = "android/rockchip/update/service/RKUpdateService";

/*---------------------------------------------------------------------------*/

/** 
 * �����ļ���, ��Ʒ��Ƶ� �ֽ�λ��, "��Ʒ�����Ϣ" ֱ���� ASCII �ִ���ʽ���. 
 *  .! : ���� ��� �����ļ���ʽ�ľ��嶨��. 
 */
#define POSITION_OF_PRODUCT_NAME                (0x8)

/** �����ļ���, ��Ʒ�����Ϣ�� �ֽڳ���. */
#define MAX_PRODUCT_NAME_LENGTH                 (64)

/** 
 * �����ļ���, �汾��Ϣ�� �ֽ�λ��. 
 * .! : "�汾��Ϣ" ���ض��� �����Ƹ�ʽ���. 
 * */
#define POSITION_OF_VERSION_INFO                (0x84)

/** �����ļ���, �汾��Ϣ��ݵ� �ֽڳ���. */
#define MAX_VERSION_LENGTH                      (4)

/*---------------------------------------------------------------------------*/

    
/**
 * ��ָ��·�����ļ��е�ָ��λ��, һ�ε� ��ȡָ�����ȵ� �ֽ����.
 * @param path
 *          Ŀ���ļ���·���ִ�. 
 * @param pos
 *          Ŀ��������ļ���, �뿪�ļ�ͷ���ֽ�λ��. 
 * @param length
 *          ���ȡ��ݵ��ֽڳ���. 
 * @param buf
 *          �������Ѿ�׼���� buffer ��ָ��. 
 *          ������ ���� ��֤ "*buf" �㹻��, ����� "length". 
 * @return
 *          ���ɹ�, ���� 0; ���򷵻� error code.
 */
static int read_bytes_from_file(const char* path, int pos, int length, char* buf)
{
    int result = 0;

    int fd = 0;         /* Ŀ���ļ� FD. */
    int bytes_read = 0;

	if ( ( fd = open(path, O_RDWR) ) < 0 )
    {
        LOGE("Fail to open image file file '%s', error : '%s'.", path, strerror(errno) );
        result = fd;
        goto EXIT;
	}
    
    if ( lseek(fd, pos, SEEK_SET) < 0 )
    {
        result = -1;
        LOGE("Fail to seek image file file '%s', error : '%s'.", path, strerror(errno) );
        goto EXIT;
    }

    if ( ( bytes_read = read(fd, buf, length ) ) < length )
    {
        LOGE("Fail to read image file file '%s', bytes read : '%d', error : '%s'.", path, bytes_read, strerror(errno) );
        result = errno;
        goto EXIT;
    }

EXIT:
    if ( fd > 0 )
    {
        close(fd);
    }

    return result;
}
    

/**
 * �������Ƹ�ʽ�� �汾��Ϣ����Ϊ �ִ���ʽ. 
 * .! : �� image �ļ���ʽ spec �����. 
 * @param binary
 *          �ض���ʽ�� ��������ʽ�� �汾��Ϣ. 
 *          Ŀǰ�� 4 �ֽ�. 
 * @param version
 *          ������׼����, �������ذ汾�ִ��� buffer ��ָ��. 
 * @return
 *          ���ɹ�, ���� 0; ���򷵻� error code.
 */
inline static void get_version_string(char* binary, char* version_string)
{
    sprintf(version_string, "%d.%d.%d", binary[3], binary[2], (binary[0] + (binary[1] << 8) ) );
}


static jstring android_UpdateService_getImageProductName(JNIEnv *env, jclass clazz, jstring j_path)
{
    char name[MAX_PRODUCT_NAME_LENGTH] = {0};        /* C �ִ���̬�� product name. */

    const char* path = NULL;      /* �м� heap ���� : C �ִ���ʽ�� �����ļ�·��. */
    
    jstring j_name = NULL;  /* "name" �� jstring ��̬, ���ڷ���. */
    //int bytes_read = 0;
	int offset = 0;
	char buf[64] = "";
    
    UNUSED(clazz);
    /* �� "j_path" ��ȡ "*path". */
    path = env->GetStringUTFChars(j_path, NULL);
    if ( NULL == path )
    {
        LOGE("Failed to get utf-8 path from 'j_path'.");
        goto EXIT;
    }
    LOGD("Image file path : '%s'.", path);

    if ( read_bytes_from_file(path, 0, sizeof(buf), buf) != 0 )
    {
        LOGE("Fail to read image file file '%s', error : '%s'.", path, strerror(errno) );
        goto EXIT;
    }
    if( *((unsigned int*)buf) == 0x57464B52 )//new pack tool
    {
        offset = *(unsigned int*)(buf+0x21);
    }

    /* �Ӿ����ļ���ȡ ���õĲ�Ʒ���. */
    if ( read_bytes_from_file(path, offset+POSITION_OF_PRODUCT_NAME, sizeof(name), name) != 0 )
    {
        LOGE("Fail to read image file file '%s', error : '%s'.", path, strerror(errno) );
        goto EXIT;
    }

    /* ����ȡ�� ��Ʒ����ִ� "̫��", ��... */
    if ( strlen(name) > sizeof(name) ) 
    {
        LOGE("Read invalid (too long) name info(length : '%zu'). Image file must be invalid!", strlen(name) );
        goto EXIT;
    }
    LOGD("Porduce name : '%s'.", name);
    
    /* �� "name" ת��Ϊ "j_name". */
    j_name = env->NewStringUTF(name);
    
EXIT:
    /* �ͷ� �м���Դ. */
    if ( NULL != path )
    {
		env->ReleaseStringUTFChars(j_path, path);
    }

    return j_name;
}


static jstring android_UpdateService_getImageVersion(JNIEnv *env, jclass clazz, jstring j_path)
{
    char version_binary[MAX_VERSION_LENGTH] = {0};      /* ��������̬�� �汾��Ϣ. */
    char version[16] = {0};         /* �汾��Ϣ, �ִ���ʽ. */

    const char* path = NULL;        /* �м� heap ���� : C �ִ���ʽ�� �����ļ�·��. */
    
    jstring j_version = NULL;          /* "version" �� jstring ��̬, ���ڷ���. */
	int offset = 0;
	char buf[64] = "";

    UNUSED(clazz);

    /* �� "j_path" ��ȡ "*path". */
    path = env->GetStringUTFChars(j_path, NULL);
    if ( NULL == path )
    {
        LOGE("Failed to get utf-8 path from 'j_path'.");
        goto EXIT;
    }
    LOGD("Image file path : '%s'.", path);

    if ( read_bytes_from_file(path, 0, sizeof(buf), buf) != 0 )
    {
        LOGE("Fail to read image file file '%s', error : '%s'.", path, strerror(errno) );
        goto EXIT;
    }
    if( *((unsigned int*)buf) == 0x57464B52 )//new pack tool
    {
        offset = *(unsigned int*)(buf+0x21);
    }

    /* �Ӿ����ļ���ȡ �汾��Ϣ. */
    if ( read_bytes_from_file(path, offset+POSITION_OF_VERSION_INFO, sizeof(version_binary), version_binary) != 0 )
    {
        LOGE("Fail to read image file file '%s', error : '%s'.", path, strerror(errno) );
        goto EXIT;
    }
    
    /* �� �����Ƹ�ʽ����Ϊ X.X.X ���ִ���ʽ. */
    get_version_string(version_binary, version);
    LOGD("Image version : '%s'.", version);
        
    /* �� "version" ת��Ϊ "j_version". */
    j_version = env->NewStringUTF(version);
    
EXIT:
    /* �ͷ� �м���Դ. */
    if ( NULL != path )
    {
		env->ReleaseStringUTFChars(j_path, path);
    }

    return j_version;
}

/*---------------------------------------------------------------------------*/

/*
 * JNI registration.
 */
static JNINativeMethod methods[] = {
    /* name,                    signature,                                  funcPtr */
    { "getImageVersion",        "(Ljava/lang/String;)Ljava/lang/String;",   (void *)android_UpdateService_getImageVersion },
    { "getImageProductName",    "(Ljava/lang/String;)Ljava/lang/String;",   (void *)android_UpdateService_getImageProductName},
}; 


static int registerNativeMethods(JNIEnv* env, const char* className,
    JNINativeMethod* gMethods, int numMethods)
{
    jclass clazz;

    clazz = env->FindClass(className);
    if (clazz == NULL) {
        LOGE("Native registration unable to find class '%s'", className);
        return JNI_FALSE;
    }
    if (env->RegisterNatives(clazz, gMethods, numMethods) < 0) {
        LOGE("RegisterNatives failed for '%s'", className);
        return JNI_FALSE;
    }

    return JNI_TRUE;
}

/*
 * Register native methods for all classes we know about.
 *
 * returns JNI_TRUE on success.
 */
static int registerNatives(JNIEnv* env)
{
  if (!registerNativeMethods(env, classPathName,
		  methods, sizeof(methods) / sizeof(methods[0]))) {
    return JNI_FALSE;
  }

  return JNI_TRUE;
}

// ----------------------------------------------------------------------------

/*
 * This is called by the VM when the shared library is first loaded.
 */

typedef union {
    JNIEnv* env;
    void* venv;
} UnionJNIEnvToVoid;

jint JNI_OnLoad(JavaVM* vm, void* reserved)
{
    UnionJNIEnvToVoid uenv;
    uenv.venv = NULL;
    jint result = -1;
    JNIEnv* env = NULL;

    LOGI("JNI_OnLoad");
    UNUSED(reserved);

    if (vm->GetEnv(&uenv.venv, JNI_VERSION_1_4) != JNI_OK) {
        LOGE("ERROR: GetEnv failed");
        goto bail;
    }
    env = uenv.env;

    if (registerNatives(env) != JNI_TRUE) {
        LOGE("ERROR: registerNatives failed");
        goto bail;
    }

    result = JNI_VERSION_1_4;

bail:
    return result;
}

