#include "physfs.h"
#include "soloud_file.h"
#include "soloud_physfs_ext.h"
#include "stdio.h"

namespace SoLoud
{
    class PhysFSFile : public SoLoud::File
    {
        public:
            PHYSFS_File* mFileHandle;
            virtual int eof();
            virtual unsigned int read(unsigned char* aDst, unsigned int aBytes);
            virtual unsigned int length();
            virtual unsigned int pos();
            virtual void seek(int aOffset);
            virtual ~PhysFSFile();
            PhysFSFile();
            PhysFSFile(PHYSFS_File *fp);
    };

    PhysFSFile::PhysFSFile(PHYSFS_File* fp):
        mFileHandle(fp)
        {

        }

    unsigned int PhysFSFile::read(unsigned char *aDst, unsigned int aBytes) {
        auto result = PHYSFS_readBytes(mFileHandle, aDst, aBytes);
        if (result == -1) {
            printf("PHYSFS: %s\n", PHYSFS_getLastError());
        }
        return (unsigned int)result;
    }

    unsigned int PhysFSFile::length() {
        auto result = PHYSFS_fileLength(mFileHandle);
        if (result == -1) {
            printf("PHYSFS: %s\n", PHYSFS_getLastError());
        }
        return (unsigned int)result;
    }

    void PhysFSFile::seek(int aOffset) {
        auto result = PHYSFS_seek(mFileHandle, aOffset);
        if (result == 0) {
            printf("PHYSFS: %s\n", PHYSFS_getLastError());
        }
    }

    unsigned int PhysFSFile::pos() {
        auto result = PHYSFS_tell(mFileHandle);
        if (result == -1) {
            printf("PHYSFS: %s\n", PHYSFS_getLastError());
        }
        return (unsigned int)result;
    }

    int PhysFSFile::eof() {
        return PHYSFS_eof(mFileHandle);
    }

    PhysFSFile::~PhysFSFile() {
      if (mFileHandle) {
        auto result = PHYSFS_close(mFileHandle);
        if (result == 0) {
            printf("PHYSFS: %s\n", PHYSFS_getLastError());
        }
      }
    }
}
extern "C" {
  SoloudPhysFSFile SoloudPhysFSFile_create(void* aHandle) {
    return (void*)new SoLoud::PhysFSFile((PHYSFS_File*)aHandle);
  }
}
