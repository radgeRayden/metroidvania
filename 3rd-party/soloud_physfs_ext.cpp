#include "soloud_physfs_ext.h"
#include <physfs.h>
#include <soloud_file.h>
#include <stdio.h>

namespace {
class PhysFSFile : public SoLoud::File {
public:
    ~PhysFSFile();
    PhysFSFile(PHYSFS_File* fp);
    int eof() override;
    unsigned int read(unsigned char* aDst, unsigned int aBytes) override;
    unsigned int length() override;
    unsigned int pos() override;
    void seek(int aOffset) override;

private:
    PHYSFS_File* mFileHandle;
};

PhysFSFile::PhysFSFile(PHYSFS_File* fp)
    : mFileHandle(fp)
{
}

unsigned int PhysFSFile::read(unsigned char* aDst, unsigned int aBytes)
{
    const auto result = PHYSFS_readBytes(mFileHandle, aDst, aBytes);
    if (result == -1) {
        printf("PHYSFS: %s\n", PHYSFS_getLastError());
    }
    return static_cast<unsigned int>(result);
}

unsigned int PhysFSFile::length()
{
    const auto result = PHYSFS_fileLength(mFileHandle);
    if (result == -1) {
        printf("PHYSFS: %s\n", PHYSFS_getLastError());
    }
    return static_cast<unsigned int>(result);
}

void PhysFSFile::seek(int aOffset)
{
    const auto result = PHYSFS_seek(mFileHandle, aOffset);
    if (result == 0) {
        printf("PHYSFS: %s\n", PHYSFS_getLastError());
    }
}

unsigned int PhysFSFile::pos()
{
    const auto result = PHYSFS_tell(mFileHandle);
    if (result == -1) {
        printf("PHYSFS: %s\n", PHYSFS_getLastError());
    }
    return static_cast<unsigned int>(result);
}

int PhysFSFile::eof()
{
    return PHYSFS_eof(mFileHandle);
}

PhysFSFile::~PhysFSFile()
{
    if (mFileHandle) {
        const auto result = PHYSFS_close(mFileHandle);
        if (result == 0) {
            printf("PHYSFS: %s\n", PHYSFS_getLastError());
        }
    }
}
}

extern "C" {
SoloudPhysFSFile SoloudPhysFSFile_create(void* aHandle)
{
    return reinterpret_cast<void*>(new PhysFSFile(reinterpret_cast<PHYSFS_File*>(aHandle)));
}
void SoloudPhysFSFile_destroy(SoloudPhysFSFile aHandle)
{
    delete reinterpret_cast<PhysFSFile*>(aHandle);
}
}
